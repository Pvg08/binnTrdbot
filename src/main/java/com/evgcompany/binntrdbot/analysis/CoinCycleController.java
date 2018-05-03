/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.analysis;

import com.binance.api.client.domain.market.TickerPrice;
import com.evgcompany.binntrdbot.PeriodicProcessThread;
import com.evgcompany.binntrdbot.api.TradingAPIAbstractInterface;
import com.evgcompany.binntrdbot.coinrating.CoinRatingController;
import com.evgcompany.binntrdbot.mainApplication;
import com.evgcompany.binntrdbot.tradeCycleProcess;
import com.evgcompany.binntrdbot.tradePairProcessController;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.KShortestPaths;
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
    
    private CoinRatingController coinRatingController = null;
    private tradePairProcessController pairProcessController = null;
    private tradeCycleProcess cycleProcess = null;
    
    private TradingAPIAbstractInterface client = null;
    private final double comissionPercent;
    private boolean initialized = false;
    
    private BigDecimal tradingBalancePercent = null;
    
    private final double minProfitPercent = 1.0;
    private final double maxGlobalCoinRank = 250;
    private final double minPairRating = 2;
    private final double minPairBaseHourVolume = 6;
    
    private boolean useStopLimited = true;
    private int stopLimitTimeout = 6 * 60 * 60;//21600;

    public CoinCycleController(TradingAPIAbstractInterface client, CoinRatingController coinRatingController) {
        this.client = client;
        this.coinRatingController = coinRatingController;
        pairProcessController = coinRatingController.getPaircontroller();
        comissionPercent = pairProcessController.getProfitsChecker().getTradeComissionPercent().doubleValue();
        tradingBalancePercent = BigDecimal.valueOf(pairProcessController.getTradingBalancePercent());
    }

    private void addPairToGraph(String symbol1, String symbol2, double price) {
        if (!graph.containsVertex(symbol1)) graph.addVertex(symbol1);
        if (!graph.containsVertex(symbol2)) graph.addVertex(symbol2);

        if (!graph_pair.containsVertex(symbol1)) graph_pair.addVertex(symbol1);
        if (!graph_pair.containsVertex(symbol2)) graph_pair.addVertex(symbol2);
        if (!graph_pair.containsEdge(symbol1, symbol2)) graph_pair.addEdge(symbol1, symbol2);
        
        if (!graph.containsEdge(symbol1, symbol2)) graph.addEdge(symbol1, symbol2);
        DefaultWeightedEdge edge1 = graph.getEdge(symbol1, symbol2);
        graph.setEdgeWeight(edge1, price > 0 ? price : 0);

        if (!graph.containsEdge(symbol2, symbol1)) graph.addEdge(symbol2, symbol1);
        DefaultWeightedEdge edge2 = graph.getEdge(symbol2, symbol1);
        graph.setEdgeWeight(edge2, price > 0 ? 1/price : 0);
    }
    
    private void setPairPriceToGraph(String symbol1, String symbol2, double price) {
        addPairToGraph(symbol1, symbol2, price);
    }
    
    private void addPairToGraph(String pair, double price) {
        String[] coins = coinRatingController.getCoinInfo().getCoinPairs().get(pair);
        if (coins != null) {
            if (coinRatingController.getCoinInfo().getQuoteAssets().contains(coins[0]) || coinRatingController.getCoinInfo().getQuoteAssets().contains(coins[1])) {
                addPairToGraph(coins[0], coins[1], price);
            }
        }
    }
    
    private void initGraph() {
        graph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        graph_pair = new DefaultDirectedGraph<>(DefaultEdge.class);
        coinRatingController.getCoinInfo().getCoinPairs().forEach((pkey, coins)->{
            if (coins.length == 2) {
                addPairToGraph(coins[0], coins[1], coinRatingController.getCoinInfo().getLastPrices().get(pkey));
            }
        });
        mainApplication.getInstance().log("Coin graph built: Vertexes=" + graph.vertexSet().size() + "; Edges=" + graph.edgeSet().size() + "; Straight pairs=" + graph_pair.edgeSet().size());
        mainApplication.getInstance().log("Quote assets: " + coinRatingController.getCoinInfo().getQuoteAssets());
    }

    private double getCycleWeight(DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> graph, List<String> cycle) {
        double totalWeight = 1;
        for(int i = 1; i < cycle.size(); i++){
            double weight = graph.getEdgeWeight(graph.getEdge(cycle.get(i-1), cycle.get(i)));
            totalWeight *= weight * ((100.0-comissionPercent) / 100.0);
        }
        return totalWeight;
    }

    public List<String> getRevertPathDescription(String coinLast, String coinBase) {
        if (coinLast.equals(coinBase)) return null;
        KShortestPaths pathsF = new KShortestPaths(graph, 100, 4);
        List<GraphPath<String, DefaultWeightedEdge>> paths = pathsF.getPaths(coinLast, coinBase);
        if (paths.isEmpty()) return null;
        return getCycleDescription(paths.get(0).getVertexList());
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
            String symbol;
            if (graph_pair.containsEdge(symbol2, symbol1)) {
                symbol = symbol2 + symbol1;
            } else {
                symbol = symbol1 + symbol2;
            }
            if (pairProcessController != null) {
                if (pairProcessController.hasPair(symbol1 + symbol2) || pairProcessController.hasPair(symbol2 + symbol1)) {
                    return false;
                }
            }
            if (coinRatingController != null) {
                if (!symbol1.equals(coinRatingController.getCoinInfo().getBaseCoin()) && !coinRatingController.getCoinInfo().getQuoteAssets().contains(symbol1)) {
                    double coinrank = coinRatingController.getCoinRank(symbol1);
                    if (coinrank > maxGlobalCoinRank) {
                        //System.out.println(symbol1 + ": rank " + coinrank + " > " + maxGlobalRank);
                        return false;
                    }
                }
                if (!symbol2.equals(coinRatingController.getCoinInfo().getBaseCoin()) && !coinRatingController.getCoinInfo().getQuoteAssets().contains(symbol2)) {
                    double coinrank = coinRatingController.getCoinRank(symbol2);
                    if (coinrank > maxGlobalCoinRank) {
                        //System.out.println(symbol2 + ": rank " + coinrank + " > " + maxGlobalRank);
                        return false;
                    }
                }
                if (coinRatingController.isAnalyzer()) {
                    double coinPairVolume = coinRatingController.getPairBaseHourVolume(symbol);
                    if (coinPairVolume < minPairBaseHourVolume) {
                        //System.out.println(symbol + ": volume " + coinPairVolume + " < " + minPairBaseHourVolume);
                        return false;
                    }
                    double coinPairRating = coinRatingController.getPairRating(symbol);
                    if (coinPairRating < minPairRating) {
                        //System.out.println(symbol + ": rating " + coinPairRating + " < " + minPairRating);
                        return false;
                    }
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
        double min_weight = 1 + 0.01*minProfitPercent;
        for(List<String> cycle : cycles) {
            if (cycleIsValid(cycle)) {
                double weight = round(getCycleWeight(graph, cycle), 3);
                if (weight > min_weight && (weight > best_profit || (weight == best_profit && Math.random() < 0.5))) {
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
        cycles.forEach((cycle) -> {
            cycle.add(baseCoin);
        });
        return getBestValidCycle(cycles);
    }

    private List<String> getBestCycle() {
        List<List<String>> best_cycles = new ArrayList<>();
        for(String coin : coinRatingController.getCoinInfo().getCoinsToCheck()) {
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
            updatePrices();
            double best_profit = round(getCycleWeight(graph, best_cycle), 3);
            if (best_profit < 1 + 0.01*minProfitPercent) {
                mainApplication.getInstance().log("", true, false);
                mainApplication.getInstance().log("Found cycle: " + best_cycle + " but recheck failed!", true, true);
                return;
            }
            mainApplication.getInstance().log("", true, false);
            mainApplication.getInstance().log("Found cycle: " + best_cycle, true, true);
            mainApplication.getInstance().log("Cycle description: " + getCycleDescription(best_cycle), true, false);
            mainApplication.getInstance().log("Profit: " + best_profit, true, false);
            cycleProcess = new tradeCycleProcess(this);
            cycleProcess.setDelayTime(2);
            cycleProcess.setProfitAim(best_profit);
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

    private void runInit() {
        initialized = false;
        doWait(1000);
        mainApplication.getInstance().log("Graph thread starting...");
        mainApplication.getInstance().log("Graph init begin...");
        initGraph();
        updatePrices();
        initialized = true;
        mainApplication.getInstance().log("Cycle coins to check: " + coinRatingController.getCoinInfo().getCoinsToCheck());
        mainApplication.getInstance().log("Graph init end...");
        doWait(3000);
    }
    
    @Override
    protected void runStart() {
        if (!initialized) runInit();
    }

    @Override
    protected void runBody() {
        updatePrices();
        if ((cycleProcess == null || !cycleProcess.isAlive()) && (coinRatingController == null || (
            !coinRatingController.isAnalyzer() ||
            coinRatingController.isHaveAllCoinsPairsInfo()
        ))) {
            checkCycles();
        }
    }

    @Override
    protected void runFinish() {
        mainApplication.getInstance().log("Graph thread end...");
        initialized = false;
    }

    /**
     * @return the initialized
     */
    public boolean isInitializedGraph() {
        return initialized;
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

    /**
     * @return the coinRatingController
     */
    public CoinRatingController getCoinRatingController() {
        return coinRatingController;
    }

    /**
     * @return the tradingBalancePercent
     */
    public BigDecimal getTradingBalancePercent() {
        return tradingBalancePercent;
    }

    /**
     * @param tradingBalancePercent the tradingBalancePercent to set
     */
    public void setTradingBalancePercent(BigDecimal tradingBalancePercent) {
        this.tradingBalancePercent = tradingBalancePercent;
    }

    /**
     * @return the stopLimitTimeout
     */
    public int getStopLimitTimeout() {
        return stopLimitTimeout;
    }

    /**
     * @param stopLimitTimeout the stopLimitTimeout to set
     */
    public void setStopLimitTimeout(int stopLimitTimeout) {
        this.stopLimitTimeout = stopLimitTimeout;
    }

    /**
     * @return the useStopLimited
     */
    public boolean isUseStopLimited() {
        return useStopLimited;
    }

    /**
     * @param useStopLimited the useStopLimited to set
     */
    public void setUseStopLimited(boolean useStopLimited) {
        this.useStopLimited = useStopLimited;
    }

    public double getAbortProfit(BigDecimal initialOrderQty, BigDecimal qty, String initialSymbol, String baseSymbol, String quoteSymbol) {
        if (initialOrderQty == null || initialSymbol == null || qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            return -1;
        }
        double currentMainCoinPrice = coinRatingController.getCoinInfo().convertSumm(baseSymbol, qty.doubleValue(), initialSymbol);
        double initialPrice = initialOrderQty.doubleValue();
        double mainCoinProfit = (1 + (currentMainCoinPrice - initialPrice) / initialPrice) * ((100.0-comissionPercent) / 100.0);
        System.out.println(initialOrderQty + " " + initialSymbol);
        System.out.println(qty + " " + baseSymbol + "/" + quoteSymbol);
        System.out.println(currentMainCoinPrice + " " + initialPrice + " " + mainCoinProfit);
        return mainCoinProfit * 0.995;
    }
}
