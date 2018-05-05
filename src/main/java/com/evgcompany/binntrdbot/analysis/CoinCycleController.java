/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.analysis;

import com.evgcompany.binntrdbot.PeriodicProcessThread;
import com.evgcompany.binntrdbot.api.TradingAPIAbstractInterface;
import com.evgcompany.binntrdbot.coinrating.CoinInfoAggregator;
import com.evgcompany.binntrdbot.coinrating.CoinRatingController;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.KShortestPaths;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;

/**
 *
 * @author EVG_adm_T
 */
public class CoinCycleController extends PeriodicProcessThread {
    private CoinRatingController coinRatingController = null;
    private tradePairProcessController pairProcessController = null;
    private tradeCycleProcess cycleProcess = null;
    private CoinInfoAggregator info = null;
    
    private TradingAPIAbstractInterface client = null;
    private final double comissionPercent;
    
    private BigDecimal tradingBalancePercent = null;
    
    private double minProfitPercent = 1.0;
    private double maxGlobalCoinRank = 250;
    private double minPairRating = 2;
    private double minPairBaseHourVolume = 6;
    
    private Set<String> restrictCoins = new HashSet<>();
    private Set<String> requiredCoins = new HashSet<>();
    private Set<String> mainCoins = new HashSet<>();
    private boolean useStopLimited = true;
    private int stopLimitTimeout = 12 * 60 * 60;
    private int switchLimitTimeout = 30 * 60;

    public CoinCycleController(TradingAPIAbstractInterface client, CoinRatingController coinRatingController) {
        this.client = client;
        this.coinRatingController = coinRatingController;
        pairProcessController = coinRatingController.getPaircontroller();
        comissionPercent = pairProcessController.getProfitsChecker().getTradeComissionPercent().doubleValue();
        tradingBalancePercent = BigDecimal.valueOf(pairProcessController.getTradingBalancePercent());
        info = coinRatingController.getCoinInfo();
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
        KShortestPaths pathsF = new KShortestPaths(info.getPricesGraph(), 100, 4);
        List<GraphPath<String, DefaultWeightedEdge>> paths = pathsF.getPaths(coinLast, coinBase);
        if (paths.isEmpty()) return null;
        return getCycleDescription(paths.get(0).getVertexList());
    }
    
    private List<String> getCycleDescription(List<String> cycle) {
        List<String> cycle_description = new ArrayList<>();
        for(int i=1; i < cycle.size(); i++) {
            String symbol1 = cycle.get(i-1);
            String symbol2 = cycle.get(i);
            if (info.getPairsGraph().containsEdge(symbol2, symbol1)) {
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
            if (info.getPairsGraph().containsEdge(symbol2, symbol1)) { // BUY
                cycleProcess.addChain(symbol2+symbol1 + " BUY", info.getPricesGraph().getEdgeWeight(info.getPricesGraph().getEdge(symbol2, symbol1))); 
            } else { // SELL
                cycleProcess.addChain(symbol1+symbol2 + " SELL", info.getPricesGraph().getEdgeWeight(info.getPricesGraph().getEdge(symbol1, symbol2)));
            }
        }
    }

    private boolean cycleIsValid(List<String> cycle) {
        if (cycle.size() < 3 || cycle.size() > 5) return false;
        for(int i=1; i<cycle.size(); i++) {
            String symbol1 = cycle.get(i-1);
            String symbol2 = cycle.get(i);
            if (!info.getPairsGraph().containsEdge(symbol2, symbol1) && !info.getPairsGraph().containsEdge(symbol1, symbol2)) {
                return false;
            }
            if (restrictCoins.contains(symbol1) || restrictCoins.contains(symbol2)) {
                return false;
            }
            if (!requiredCoins.isEmpty()) {
                if (!requiredCoins.contains(symbol1) && !requiredCoins.contains(symbol2)) {
                    return false;
                }
            }
            String symbol;
            if (info.getPairsGraph().containsEdge(symbol2, symbol1)) {
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
                if (!symbol1.equals(info.getBaseCoin()) && !info.getQuoteAssets().contains(symbol1)) {
                    double coinrank = coinRatingController.getCoinRank(symbol1);
                    if (coinrank > maxGlobalCoinRank) {
                        //System.out.println(symbol1 + ": rank " + coinrank + " > " + maxGlobalRank);
                        return false;
                    }
                }
                if (!symbol2.equals(info.getBaseCoin()) && !info.getQuoteAssets().contains(symbol2)) {
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
                double weight = round(getCycleWeight(info.getPricesGraph(), cycle), 3);
                if ((weight > min_weight) && ((weight > best_profit || (weight == best_profit && Math.random() < 0.5)))) {
                    best_cycle = cycle;
                    best_profit = weight;
                }
            }
        }
        return best_cycle;
    }
    
    private List<String> getBestValidCoinCycle(String baseCoin) {
        TarjanSimpleCyclesFromVertex<String, DefaultWeightedEdge> simple_cycles = new TarjanSimpleCyclesFromVertex<>(info.getPricesGraph());
        List<List<String>> cycles = simple_cycles.findSimpleCycles(baseCoin);
        cycles.forEach((cycle) -> {
            cycle.add(baseCoin);
        });
        return getBestValidCycle(cycles);
    }

    private List<String> getBestCycle() {
        List<List<String>> best_cycles = new ArrayList<>();
        for(String coin : info.getCoinsToCheck()) {
            if (mainCoins.isEmpty() || mainCoins.contains(coin)) {
                List<String> best_cycle = getBestValidCoinCycle(coin);
                if (best_cycle != null) {
                    best_cycles.add(best_cycle);
                }
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
        try {
            info.getSemaphore().acquire();
            List<String> best_cycle = getBestCycle();
            if (best_cycle != null) {
                info.updatePrices();
                double best_profit = round(getCycleWeight(info.getPricesGraph(), best_cycle), 3);
                if (best_profit < 1 + 0.01*minProfitPercent) {
                    mainApplication.getInstance().log("", true, false);
                    mainApplication.getInstance().log("Found cycle: " + best_cycle + " but recheck failed!", true, true);
                } else {
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
        } catch (InterruptedException ex) {
            Logger.getLogger(CoinCycleController.class.getName()).log(Level.SEVERE, null, ex);
        }
        info.getSemaphore().release();
    }
    
    @Override
    protected void runStart() {
        mainApplication.getInstance().log("Cycle thread starting...");
        mainApplication.getInstance().log("Cycle required: " + requiredCoins);
        mainApplication.getInstance().log("Cycle restricted: " + restrictCoins);
        doWait(3000);
    }

    @Override
    protected void runBody() {
        if ((cycleProcess == null || !cycleProcess.isAlive()) && (coinRatingController == null || (
            !coinRatingController.isAnalyzer() ||
            coinRatingController.isHaveAllCoinsPairsInfo()
        ))) {
            checkCycles();
        }
    }

    @Override
    protected void runFinish() {
        mainApplication.getInstance().log("Cycle thread end...");
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
        double currentMainCoinPrice = info.convertSumm(baseSymbol, qty.doubleValue(), initialSymbol);
        double initialPrice = initialOrderQty.doubleValue();
        double mainCoinProfit = (1 + (currentMainCoinPrice - initialPrice) / initialPrice) * ((100.0-comissionPercent) / 100.0);
        System.out.println(initialOrderQty + " " + initialSymbol);
        System.out.println(qty + " " + baseSymbol + "/" + quoteSymbol);
        System.out.println(currentMainCoinPrice + " " + initialPrice + " " + mainCoinProfit);
        return mainCoinProfit * 0.99;
    }

    /**
     * @return the restrictCoins
     */
    public Set<String> getRestrictCoins() {
        return restrictCoins;
    }

    /**
     * @param restrictCoins the restrictCoins to set
     */
    public void setRestrictCoins(String restrictCoins) {
        this.restrictCoins = new HashSet<>(Arrays.asList(restrictCoins.toUpperCase().trim().split(",")));
    }

    /**
     * @return the requiredCoins
     */
    public Set<String> getRequiredCoins() {
        return requiredCoins;
    }

    /**
     * @param requiredCoins the requiredCoins to set
     */
    public void setRequiredCoins(String requiredCoins) {
        this.requiredCoins = new HashSet<>(Arrays.asList(requiredCoins.toUpperCase().trim().split(",")));
    }

    /**
     * @return the mainCoins
     */
    public Set<String> getMainCoins() {
        return mainCoins;
    }

    /**
     * @param mainCoins the mainCoins to set
     */
    public void setMainCoins(String mainCoins) {
        this.mainCoins = new HashSet<>(Arrays.asList(mainCoins.toUpperCase().trim().split(",")));
    }
    
    /**
     * @return the minProfitPercent
     */
    public double getMinProfitPercent() {
        return minProfitPercent;
    }

    /**
     * @param minProfitPercent the minProfitPercent to set
     */
    public void setMinProfitPercent(double minProfitPercent) {
        this.minProfitPercent = minProfitPercent;
    }

    /**
     * @return the maxGlobalCoinRank
     */
    public double getMaxGlobalCoinRank() {
        return maxGlobalCoinRank;
    }

    /**
     * @param maxGlobalCoinRank the maxGlobalCoinRank to set
     */
    public void setMaxGlobalCoinRank(double maxGlobalCoinRank) {
        this.maxGlobalCoinRank = maxGlobalCoinRank;
    }

    /**
     * @return the minPairRating
     */
    public double getMinPairRating() {
        return minPairRating;
    }

    /**
     * @param minPairRating the minPairRating to set
     */
    public void setMinPairRating(double minPairRating) {
        this.minPairRating = minPairRating;
    }

    /**
     * @return the minPairBaseHourVolume
     */
    public double getMinPairBaseHourVolume() {
        return minPairBaseHourVolume;
    }

    /**
     * @param minPairBaseHourVolume the minPairBaseHourVolume to set
     */
    public void setMinPairBaseHourVolume(double minPairBaseHourVolume) {
        this.minPairBaseHourVolume = minPairBaseHourVolume;
    }

    /**
     * @param client the client to set
     */
    public void setClient(TradingAPIAbstractInterface client) {
        this.client = client;
    }

    /**
     * @return the switchLimitTimeout
     */
    public int getSwitchLimitTimeout() {
        return switchLimitTimeout;
    }

    /**
     * @param switchLimitTimeout the switchLimitTimeout to set
     */
    public void setSwitchLimitTimeout(int switchLimitTimeout) {
        this.switchLimitTimeout = switchLimitTimeout;
    }
}
