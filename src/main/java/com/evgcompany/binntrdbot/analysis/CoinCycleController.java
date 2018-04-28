/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.analysis;

import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.market.TickerPrice;
import com.evgcompany.binntrdbot.PeriodicProcessThread;
import com.evgcompany.binntrdbot.api.TradingAPIAbstractInterface;
import com.evgcompany.binntrdbot.mainApplication;
import com.evgcompany.binntrdbot.tradeCycleProcess;
import com.evgcompany.binntrdbot.tradePairProcessController;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultWeightedEdge;

/**
 *
 * @author EVG_adm_T
 */
public class CoinCycleController extends PeriodicProcessThread {
    DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> graph = null;
    DefaultDirectedGraph<String, DefaultEdge> graph_pair = null;
    private Set<String> basePrices = null;
    private List<String> coinsToCheck = null;
    
    private tradePairProcessController pairProcessController = null;
    private tradeCycleProcess cycleProcess = null;
    
    private TradingAPIAbstractInterface client = null;
    private final double comissionPercent;
    private boolean initialized = false;
    
    private final String baseCoin = "BTC";
    private final double baseCoinMinCount = 0.001;

    public CoinCycleController(TradingAPIAbstractInterface client, tradePairProcessController pairProcessController, String coins) {
        this.client = client;
        this.pairProcessController = pairProcessController;
        this.comissionPercent = pairProcessController.getProfitsChecker().getTradeComissionPercent().doubleValue();
        coinsToCheck = Arrays.asList(coins.split(","));
    }

    private void addPairToGraph(String symbol1, String symbol2, double price) {
        if (!graph.containsVertex(symbol1)) graph.addVertex(symbol1);
        if (!graph.containsVertex(symbol2)) graph.addVertex(symbol2);

        if (!graph_pair.containsVertex(symbol1)) graph_pair.addVertex(symbol1);
        if (!graph_pair.containsVertex(symbol2)) graph_pair.addVertex(symbol2);
        if (!graph_pair.containsEdge(symbol1, symbol2)) graph_pair.addEdge(symbol1, symbol2);
        
        if (!graph.containsEdge(symbol1, symbol2)) graph.addEdge(symbol1, symbol2);
        DefaultWeightedEdge edge1 = graph.getEdge(symbol1, symbol2);
        graph.setEdgeWeight(edge1, price * (100.0-comissionPercent) / 100.0);

        if (!graph.containsEdge(symbol2, symbol1)) graph.addEdge(symbol2, symbol1);
        DefaultWeightedEdge edge2 = graph.getEdge(symbol2, symbol1);
        graph.setEdgeWeight(edge2, (1/price) * (100.0-comissionPercent) / 100.0);
    }
    
    private void setPairPriceToGraph(String symbol1, String symbol2, double price) {
        addPairToGraph(symbol1, symbol2, price);
    }
    
    private void addPairToGraph(String pair, double price) {
        for(int i=1; i < pair.length() - 2; i++) {
            String symbol1 = pair.substring(0, pair.length() - i - 1);
            String symbol2 = pair.substring(pair.length() - i - 1, pair.length());
            if (basePrices.contains(symbol1) || basePrices.contains(symbol2)) {
                addPairToGraph(symbol1, symbol2, price);
            }
        }
    }
    
    private void initGraph() {
        graph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        graph_pair = new DefaultDirectedGraph<>(DefaultEdge.class);
        basePrices = new HashSet<>();
        List<TickerPrice> allPrices = client.getAllPrices();
        allPrices.forEach((price) -> {
            if (price != null && !price.getSymbol().isEmpty()) {
                SymbolInfo info = client.getSymbolInfo(price.getSymbol().toUpperCase());
                if (info != null) {
                    addPairToGraph(info.getBaseAsset(), info.getQuoteAsset(), Double.valueOf(price.getPrice()));
                    basePrices.add(info.getQuoteAsset());
                }
                doWait(50);
            }
        });
        mainApplication.getInstance().log("Coin graph built: Vertexes=" + graph.vertexSet().size() + "; Edges=" + graph.edgeSet().size() + "; Straight pairs=" + graph_pair.edgeSet().size());
        mainApplication.getInstance().log("Quote assets: " + basePrices);
    }

    private double getCycleWeight(DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> graph, List<String> cycle) {
        double totalWeight = 1;
        for(int i = 1; i < cycle.size(); i++){
            double weight = graph.getEdgeWeight(graph.getEdge(cycle.get(i-1), cycle.get(i)));
            totalWeight *= weight;
        }
        return totalWeight;
    }

    private List<String> getCycleDescription(List<String> cycle) {
        List<String> cycle_description = new ArrayList<>();
        for(int i=1; i < cycle.size(); i++) {
            String symbol1 = cycle.get(i-1);
            String symbol2 = cycle.get(i);
            if (graph_pair.containsEdge(symbol2, symbol1)) {
                cycle_description.add(symbol2+symbol1 + " BUY");
            } else {
                cycle_description.add(symbol1+symbol2 + " SELL");
            }
        }
        return cycle_description;
    }

    private void addCycleToCycleChain(List<String> cycle) {
        for(int i=1; i < cycle.size(); i++) {
            String symbol1 = cycle.get(i-1);
            String symbol2 = cycle.get(i);
            if (graph_pair.containsEdge(symbol2, symbol1)) { // BUY
                cycleProcess.addChain(symbol2+symbol1 + " BUY", graph.getEdgeWeight(graph.getEdge(symbol2, symbol1))); 
            } else { // SELL
                cycleProcess.addChain(symbol1+symbol2 + " SELL", graph.getEdgeWeight(graph.getEdge(symbol1, symbol2)));
            }
        }
    }

    private boolean cycleIsValid(List<String> cycle) {
        if (cycle.size() < 3 || cycle.size() > 5) return false;
        for(int i=1; i<cycle.size(); i++) {
            String symbol1 = cycle.get(i-1);
            String symbol2 = cycle.get(i);
            if (!graph_pair.containsEdge(symbol2, symbol1) && !graph_pair.containsEdge(symbol1, symbol2)) {
                return false;
            }
            if (pairProcessController != null) {
                if (pairProcessController.hasPair(symbol1 + symbol2) || pairProcessController.hasPair(symbol2 + symbol1)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static double round(double value, int places) {
        if (places < 0) places = 0;
        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    private List<String> getBestValidCycle(List<List<String>> cycles) {
        double best_profit = 0;
        List<String> best_cycle = null;
        for(List<String> cycle : cycles) {
            if (cycleIsValid(cycle)) {
                double weight = round(getCycleWeight(graph, cycle), 3);
                if (weight > 1.02 && (weight > best_profit || (weight == best_profit && Math.random() < 0.5))) {
                    best_cycle = cycle;
                    best_profit = weight;
                }
            }
        }
        return best_cycle;
    }
    
    private List<String> getBestValidCoinCycle(String baseCoin) {
        TarjanSimpleCyclesFromVertex<String, DefaultWeightedEdge> simple_cycles = new TarjanSimpleCyclesFromVertex<>(graph);
        List<List<String>> cycles = simple_cycles.findSimpleCycles(baseCoin);
        for(List<String> cycle : cycles) {
            cycle.add(baseCoin);
        }
        return getBestValidCycle(cycles);
    }

    private List<String> getBestCycle() {
        List<List<String>> best_cycles = new ArrayList<>();
        for(String coin : coinsToCheck) {
            List<String> best_cycle = getBestValidCoinCycle(coin);
            if (best_cycle != null) {
                best_cycles.add(best_cycle);
            }
        }
        if (best_cycles.size() >= 2) {
            return getBestValidCycle(best_cycles);
        } else if (best_cycles.size() == 1) {
            return best_cycles.get(0);
        }
        return null;
    }

    private void checkCycles() {
        List<String> best_cycle = getBestCycle();
        if (best_cycle != null) {
            double best_profit = round(getCycleWeight(graph, best_cycle), 3);
            mainApplication.getInstance().log("", true, false);
            mainApplication.getInstance().log("Found cycle: " + best_cycle, true, true);
            mainApplication.getInstance().log("Cycle description: " + getCycleDescription(best_cycle), true, false);
            mainApplication.getInstance().log("Profit: " + best_profit, true, false);
            cycleProcess = new tradeCycleProcess(this);
            cycleProcess.setDelayTime(2);
            addCycleToCycleChain(best_cycle);
            cycleProcess.start();
        }
    }

    public double convertSumm(String symbol1, double price, String symbol2) {
        if (graph.containsEdge(symbol1, symbol2)) {
            return price * graph.getEdgeWeight(graph.getEdge(symbol1, symbol2));
        }
        return 0;
    }
    
    public void updatePrices() {
        List<TickerPrice> allPrices = client.getAllPrices();
        updatePrices(allPrices);
    }

    public void updatePrices(List<TickerPrice> allPrices) {
        allPrices.forEach((price) -> {
            addPairToGraph(price.getSymbol().toUpperCase(), Double.valueOf(price.getPrice()));
        });
    }

    public void runInit() {
        initialized = false;
        
        doWait(1000);
        
        mainApplication.getInstance().log("Graph thread starting...");
        mainApplication.getInstance().log("Graph init begin...");
        initGraph();
        
        updatePrices();
        List<String> oldCoinsToCheck = coinsToCheck;
        coinsToCheck = new ArrayList<>();
        oldCoinsToCheck.forEach((checkCoin) -> {
            AssetBalance balance = client.getAssetBalance(checkCoin);
            if (balance != null && !balance.getAsset().isEmpty()) {
                double full_balance = Double.parseDouble(balance.getFree()) + Double.parseDouble(balance.getLocked());
                if (
                    (checkCoin.equals(baseCoin) && full_balance >= baseCoinMinCount) || 
                    convertSumm(checkCoin, full_balance, baseCoin) >= baseCoinMinCount
                ) {
                    coinsToCheck.add(checkCoin);
                }
            }
        });
        mainApplication.getInstance().log("Cycle coins to check: " + coinsToCheck);
        
        mainApplication.getInstance().log("Graph init end...");
        initialized = true;
        doWait(3000);
        checkCycles();
    }
    
    @Override
    protected void runStart() {
        if (!initialized) runInit();
    }

    @Override
    protected void runBody() {
        updatePrices();
        if (cycleProcess == null || !cycleProcess.isAlive()) {
            checkCycles();
        }
    }

    @Override
    protected void runFinish() {
        mainApplication.getInstance().log("Graph thread end...");
        initialized = false;
    }

    /**
     * @return the basePrices
     */
    public Set<String> getBasePrices() {
        return basePrices;
    }

    /**
     * @return the initialized
     */
    public boolean isInitializedGraph() {
        return initialized;
    }

    /**
     * @return the coinsToCheck
     */
    public List<String> getCoinsToCheck() {
        return coinsToCheck;
    }

    /**
     * @return the client
     */
    public TradingAPIAbstractInterface getClient() {
        return client;
    }

    /**
     * @return the pairProcessController
     */
    public tradePairProcessController getPairProcessController() {
        return pairProcessController;
    }
}
