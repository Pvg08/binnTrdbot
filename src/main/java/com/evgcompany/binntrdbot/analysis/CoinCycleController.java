/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.analysis;

import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.market.TickerPrice;
import com.evgcompany.binntrdbot.PeriodicProcessThread;
import com.evgcompany.binntrdbot.api.TradingAPIAbstractInterface;
import com.evgcompany.binntrdbot.mainApplication;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;

/**
 *
 * @author EVG_adm_T
 */
public class CoinCycleController extends PeriodicProcessThread {
    DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> graph = null;
    private Set<String> basePrices = null;
    
    private TradingAPIAbstractInterface client = null;
    private final double comissionPercent;
    private boolean initialized = false;
    
    public CoinCycleController(TradingAPIAbstractInterface client, double comissionPercent) {
        this.client = client;
        this.comissionPercent = comissionPercent;
    }
    
    private void addPairToGraph(String symbol1, String symbol2, double price) {
        if (!graph.containsVertex(symbol1)) {
            graph.addVertex(symbol1);
        }
        if (!graph.containsVertex(symbol2)) {
            graph.addVertex(symbol2);
        }

        if (!graph.containsEdge(symbol1, symbol2)) {
            graph.addEdge(symbol1, symbol2);
        }
        DefaultWeightedEdge edge1 = graph.getEdge(symbol1, symbol2);
        graph.setEdgeWeight(edge1, price * (100.0-comissionPercent) / 100.0);

        if (!graph.containsEdge(symbol2, symbol1)) {
            graph.addEdge(symbol2, symbol1);
        }
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
        mainApplication.getInstance().log("Coin graph built: Vertexes=" + graph.vertexSet().size() + "; Edges=" + graph.edgeSet().size());
        mainApplication.getInstance().log("Quote assets: " + basePrices);
    }

    private double getCycleWeight(DefaultDirectedWeightedGraph<String, DefaultWeightedEdge> graph, List<String> cycle) {
        double totalWeight = 1;
        for(int i = 1; i < cycle.size(); i++){
            double weight = graph.getEdgeWeight(graph.getEdge(cycle.get(i-1), cycle.get(i)));
            totalWeight *= weight;
        }
        double weightBackToStart = graph.getEdgeWeight(graph.getEdge(cycle.get(cycle.size()-1), cycle.get(0)));
        return totalWeight * weightBackToStart;
    }

    private void checkCycles(String baseCoin) {
        TarjanSimpleCyclesFromVertex<String, DefaultWeightedEdge> simple_cycles = new TarjanSimpleCyclesFromVertex<>(graph);
        List<List<String>> cycles = simple_cycles.findSimpleCycles(baseCoin);
        double best_profit = 0;
        List<String> best_cycle = null;
        for(List<String> cycle : cycles) {
            double weight = getCycleWeight(graph, cycle);
            if (weight > best_profit && cycle.size() <= 5 && weight > 1.02) {
                best_cycle = cycle;
                best_profit = weight;
            }
        }
        if (best_cycle != null) {
            best_cycle.add(baseCoin);
            mainApplication.getInstance().log("");
            mainApplication.getInstance().log("Found cycle: " + best_cycle);
            mainApplication.getInstance().log("Profit: " + best_profit);
        }
    }
    
    private void updatePrices() {
        List<TickerPrice> allPrices = client.getAllPrices();
        allPrices.forEach((price) -> {
            addPairToGraph(price.getSymbol().toUpperCase(), Double.valueOf(price.getPrice()));
        });
    }
    
    @Override
    protected void runStart() {
        initialized = false;
        mainApplication.getInstance().log("Graph thread starting...");
        mainApplication.getInstance().log("Graph init begin...");
        initGraph();
        mainApplication.getInstance().log("Graph init end...");
        checkCycles("BTC");
        checkCycles("NEO");
        initialized = true;
    }

    @Override
    protected void runBody() {
        updatePrices();
        checkCycles("BTC");
        checkCycles("NEO");
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
}
