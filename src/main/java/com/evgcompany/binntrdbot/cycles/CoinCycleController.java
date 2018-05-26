/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.cycles;

import com.evgcompany.binntrdbot.PeriodicProcessThread;
import com.evgcompany.binntrdbot.api.TradingAPIAbstractInterface;
import com.evgcompany.binntrdbot.coinrating.CoinInfoAggregator;
import com.evgcompany.binntrdbot.coinrating.CoinRatingController;
import com.evgcompany.binntrdbot.coinrating.CoinRatingPairLogItem;
import com.evgcompany.binntrdbot.coinrating.DepthCacheProcess;
import com.evgcompany.binntrdbot.mainApplication;
import com.evgcompany.binntrdbot.TradePairProcessList;
import com.evgcompany.binntrdbot.analysis.TarjanSimpleCyclesFromVertex;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.KShortestPaths;
import org.jgrapht.graph.DefaultWeightedEdge;

/**
 *
 * @author EVG_adm_T
 */
public class CoinCycleController extends PeriodicProcessThread {
    
    public class SwapInfo {
        public List<String> cycle;
        public int pre_size;
        public double new_weight;
    }
    
    private CoinRatingController coinRatingController = null;
    private TradePairProcessList pairProcessController = null;
    private final List<TradeCycleProcess> cycleProcesses = new ArrayList<>();
    private CoinInfoAggregator info = null;
    
    private TradingAPIAbstractInterface client = null;
    private double comissionPercent = 0.0;
    
    private BigDecimal tradingBalancePercent = null;
    
    private double minProfitPercent = 1.0;
    private double maxGlobalCoinRank = 250;
    private double minPairRating = 2;
    private double minPairBaseHourVolume = 6;
    
    private Set<String> restrictCoins = new HashSet<>();
    private Set<String> requiredCoins = new HashSet<>();
    private Set<String> mainCoins = new HashSet<>();
    
    private int maxSwitchesCount = 3;
    private int maxActiveCyclesCount = 2;
    
    private boolean useStopLimited = true;
    private int stopLimitTimeout = 12 * 60 * 60;
    private int stopFirstLimitTimeout = 12 * 60;
    private int switchLimitTimeout = 30 * 60;
    
    private int minCycleLength = 2;
    private int maxCycleLength = 5;
    
    private boolean useDepsetUpdates = true;
    private double maxPairVolumeForUsingDepsets = 10;
    private int depsetSizeLimit = 60;
    
    private long lastDepsetSetMillis = 0;
    private long depsetUpdateTime = 25 * 60;
    
    private double depsetBidAskAddPercent = 0.1;

    public CoinCycleController(TradingAPIAbstractInterface client, CoinRatingController coinRatingController) {
        this.client = client;
        this.coinRatingController = coinRatingController;
        pairProcessController = coinRatingController.getPaircontroller();
        tradingBalancePercent = BigDecimal.valueOf(pairProcessController.getTradingBalancePercent());
        info = CoinInfoAggregator.getInstance();
        if (info.getClient() == null) info.setClient(client);
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

    private boolean pathIsValid(List<String> cycle, List<String> pre_cycle) {
        if (cycle.size() < minCycleLength || cycle.size() > maxCycleLength) return false;
        if (pre_cycle != null && pre_cycle.size() > 0) {
            if (!cycle.get(0).equals(pre_cycle.get(0))) {
                return false;
            }
            if (!cycle.get(pre_cycle.size()-1).equals(pre_cycle.get(pre_cycle.size()-1))) {
                return false;
            }
        }
        for(int i = pre_cycle != null ? pre_cycle.size() : 1; i < cycle.size(); i++) {
            String symbol1 = cycle.get(i-1);
            String symbol2 = cycle.get(i);
            String pairSymbol;
            if (info.getPairsGraph().containsEdge(symbol2, symbol1)) {
                pairSymbol = symbol2 + symbol1;
            } else if (info.getPairsGraph().containsEdge(symbol1, symbol2)) {
                pairSymbol = symbol1 + symbol2;
            } else {
                return false;
            }
            if (!restrictCoins.isEmpty()) {
                if (restrictCoins.contains(symbol1) || restrictCoins.contains(symbol2)) {
                    return false;
                }
            }
            if (!requiredCoins.isEmpty()) {
                if (!requiredCoins.contains(symbol1) && !requiredCoins.contains(symbol2)) {
                    return false;
                }
            }
            if (pairProcessController != null) {
                if (pairProcessController.hasPair(pairSymbol)) {
                    return false;
                }
            }
            if (!cycleProcesses.isEmpty()) {
                for(TradeCycleProcess proc : cycleProcesses) {
                    if (proc.getUsedPairs().contains(pairSymbol)) return false;
                }
            }
            if (!symbol1.equals(info.getBaseCoin()) && !info.getQuoteAssets().contains(symbol1)) {
                double coinrank = coinRatingController.getCoinRank(symbol1);
                if (coinrank > maxGlobalCoinRank) {
                    //System.out.println(symbol1 + ": rank " + coinrank + " > " + maxGlobalCoinRank);
                    return false;
                }
            }
            if (!symbol2.equals(info.getBaseCoin()) && !info.getQuoteAssets().contains(symbol2)) {
                double coinrank = coinRatingController.getCoinRank(symbol2);
                if (coinrank > maxGlobalCoinRank) {
                    //System.out.println(symbol2 + ": rank " + coinrank + " > " + maxGlobalCoinRank);
                    return false;
                }
            }
            if (coinRatingController.isAnalyzer()) {
                double coinPairVolume = coinRatingController.getPairBaseHourVolume(pairSymbol);
                if (coinPairVolume < minPairBaseHourVolume) {
                    //System.out.println(symbol + ": volume " + coinPairVolume + " < " + minPairBaseHourVolume);
                    return false;
                }
                double coinPairRating = coinRatingController.getPairRating(pairSymbol);
                if (coinPairRating < minPairRating) {
                    //System.out.println(symbol + ": rating " + coinPairRating + " < " + minPairRating);
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

    public double getEdgeWeight(String symbol1, String symbol2) {
        if (useDepsetUpdates) {
            DepthCacheProcess proc = info.getDepthProcessForPair(symbol1 + symbol2);
            if (proc != null) { // selling
                if (!proc.isStopped() && !proc.isObsolete()) {
                    Map.Entry<BigDecimal, BigDecimal> ask = proc.getBestAsk();
                    if (ask != null) 
                        return ask.getKey().doubleValue() * (1-depsetBidAskAddPercent*0.01);
                }
            } else {
                proc = info.getDepthProcessForPair(symbol2 + symbol1);
                if (proc != null && !proc.isStopped() && !proc.isObsolete()) { // buying
                    Map.Entry<BigDecimal, BigDecimal> bid = proc.getBestBid();
                    if (bid != null) 
                        return 1/bid.getKey().doubleValue() * (1+depsetBidAskAddPercent*0.01);
                }
            }
        }
        /*if (proc != null) {
            if (isBuy) {
                Map.Entry<BigDecimal, BigDecimal> bid = proc.getBestBid();
                if (bid != null) {
                    System.out.println(bid.getKey() + " / " + info.getPricesGraph().getEdgeWeight(info.getPricesGraph().getEdge(symbol1, symbol2)));
                    return bid.getKey().doubleValue();
                }
            } else {
                Map.Entry<BigDecimal, BigDecimal> ask = proc.getBestAsk();
                if (ask != null) {
                    System.out.println(ask.getKey() + " / " + info.getPricesGraph().getEdgeWeight(info.getPricesGraph().getEdge(symbol1, symbol2)));
                    return ask.getKey().doubleValue();
                }
            }
        } else {
            proc = info.getDepthProcessForPair(symbol2 + symbol1);
            if (proc != null) {
                if (isBuy) {
                    Map.Entry<BigDecimal, BigDecimal> bid = proc.getBestBid();
                    if (bid != null) {
                        System.out.println(bid.getKey() + " / " + info.getPricesGraph().getEdgeWeight(info.getPricesGraph().getEdge(symbol2, symbol1)));
                        return 1/bid.getKey().doubleValue();
                    }
                } else {
                    Map.Entry<BigDecimal, BigDecimal> ask = proc.getBestAsk();
                    if (ask != null) {
                        System.out.println(ask.getKey() + " / " + info.getPricesGraph().getEdgeWeight(info.getPricesGraph().getEdge(symbol2, symbol1)));
                        return 1/ask.getKey().doubleValue();
                    }
                }
            }
        }*/
        return info.getPricesGraph().getEdgeWeight(info.getPricesGraph().getEdge(symbol1, symbol2));
    }
    private double getCycleWeight(List<String> cycle, int from, double start_weight) {
        double totalWeight = start_weight;
        for(int i = from; i < cycle.size(); i++){
            double weight = getEdgeWeight(cycle.get(i-1), cycle.get(i));
            totalWeight *= weight * ((100.0-comissionPercent) / 100.0);
        }
        return totalWeight;
    }
    private double getCycleWeight(List<String> cycle) {
        return getCycleWeight(cycle, 1, 1);
    }

    private List<List<String>> getAllCycles(String baseCoin) {
        TarjanSimpleCyclesFromVertex<String, DefaultWeightedEdge> simple_cycles = new TarjanSimpleCyclesFromVertex<>(info.getPricesGraph());
        List<List<String>> cycles = simple_cycles.findSimpleCycles(baseCoin);
        cycles.forEach((cycle) -> {
            cycle.add(baseCoin);
        });
        return cycles;
    }
    private List<List<String>> getAllCycles() {
        TarjanSimpleCyclesFromVertex<String, DefaultWeightedEdge> simple_cycles = new TarjanSimpleCyclesFromVertex<>(info.getPricesGraph());
        List<List<String>> cycles = new ArrayList<>(); 
        for(String baseCoin : info.getCoinsToCheck()) {
            if (mainCoins.isEmpty() || mainCoins.contains(baseCoin)) {
                List<List<String>> cycles_coin = simple_cycles.findSimpleCycles(baseCoin);
                cycles_coin.forEach((ccycle) -> {
                    ccycle.add(baseCoin);
                });
                cycles.addAll(cycles_coin);
            }
        }
        return cycles;
    }
    
    private List<List<String>> getAllValidCycles(List<List<String>> cycles, List<String> pre_cycle) {
        List<List<String>> valid_cycles = new ArrayList<>();
        for(List<String> cycle : cycles) {
            if (pathIsValid(cycle, pre_cycle)) valid_cycles.add(cycle);
        }
        return valid_cycles;
    }
    private List<List<String>> getAllValidCycles(List<List<String>> cycles) {
        return getAllValidCycles(cycles, null);
    }
    
    private double getCycleRating(List<String> cycle) {
        double rating = 1;
        int csize = cycle.size();
        if (csize <= 4) rating*=1.3;
        if (csize == 5) rating*=1.15;
        if (csize == 6) rating*=1.05;

        if (coinRatingController.isAnalyzer()) {
            
            double mult_strat = Math.pow(3, 1.0/csize);
            double mult_depth = Math.pow(3, 1.0/csize);
            
            for(int i = 1; i < csize; i++){
                String coin1 = cycle.get(i-1);
                String coin2 = cycle.get(i);
                String pair = "";
                boolean isBuy = false;
                if (info.getPairsGraph().containsEdge(coin1, coin2)) {
                    pair = coin1 + coin2;
                    isBuy = false;
                } else if (info.getPairsGraph().containsEdge(coin2, coin1)) {
                    pair = coin2 + coin1;
                    isBuy = true;
                } else {
                    rating = 0;
                }
                if (!pair.isEmpty()) {
                    if (info.getDepthProcessForPair(pair) != null) {
                        rating *= mult_depth;
                    }
                    CoinRatingPairLogItem logitem = coinRatingController.getCoinPairRatingMap().get(pair);
                    if (logitem != null) {
                        rating *= Math.pow(logitem.rating, 0.05/csize);
                        if (isBuy) {
                            if (logitem.strategies_shouldexit_rate > (logitem.strategies_shouldenter_rate + 3)) {
                                rating *= mult_strat;
                            } else if ((logitem.strategies_shouldexit_rate + 3) < logitem.strategies_shouldenter_rate) {
                                rating *= 1/mult_strat;
                            }
                        } else {
                            if (logitem.strategies_shouldexit_rate > (logitem.strategies_shouldenter_rate + 3)) {
                                rating *= 1/mult_strat;
                            } else if ((logitem.strategies_shouldexit_rate + 3) < logitem.strategies_shouldenter_rate) {
                                rating *= mult_strat;
                            }
                        }
                    } else {
                        rating = 0;
                    }
                }
            }
            System.out.println("Cycle " + cycle + " rating = " + rating);
        }

        return rating;
    }
    
    private List<String> getBestCycleFromList(List<List<String>> valid_cycles, List<String> pre_cycle, double pre_weight) {
        List<String> best_cycle = null;
        double min_weight = 1 + 0.01*minProfitPercent;
        double max_rating = Double.MIN_VALUE;
        for(List<String> cycle : valid_cycles) {
            double weight = round(getCycleWeight(cycle, pre_cycle != null ? pre_cycle.size() : 1, pre_weight), 4);
            if (weight > min_weight) {
                double rating = Math.pow(weight, 5) * (999.5 + Math.random()) * 0.001;
                rating *= getCycleRating(cycle);
                if (rating > max_rating) {
                    max_rating = rating;
                    best_cycle = cycle;
                }
            }
        }
        return best_cycle;
    }
    private List<String> getBestCycleFromList(List<List<String>> cycles) {
        return getBestCycleFromList(cycles, null, 1);
    }
    
    private List<String> getBestCyclesForEnter() {
        List<List<String>> cycles = getAllCycles();
        cycles = getAllValidCycles(cycles);
        info.updatePrices();
        return getBestCycleFromList(cycles);
    }
    
    private List<String> getBestCyclesForEnter(List<String> pre_cycle, double pre_weight) {
        if (pre_cycle == null || pre_cycle.isEmpty()) {
            return getBestCyclesForEnter();
        }
        String coin = pre_cycle.get(0);
        List<List<String>> cycles = getAllCycles(coin);
        cycles = getAllValidCycles(cycles, pre_cycle);
        info.updatePrices();
        return getBestCycleFromList(cycles, pre_cycle, pre_weight);
    }
    
    private Set<String> getPairsToDepthCheck(int limitValue) {
        List<List<String>> cycles = getAllCycles();
        cycles = getAllValidCycles(cycles);
        Set<String> result = new HashSet<>();
        Map<String, Integer> pairs_usage = new HashMap<>();
        int max_usage = -1;
        for(List<String> cycle : cycles) {
            for(int i = 1; i < cycle.size(); i++){
                String coin1 = cycle.get(i-1);
                String coin2 = cycle.get(i);
                String pair = "";
                if (info.getPairsGraph().containsEdge(coin1, coin2)) {
                    pair = coin1 + coin2;
                } else if (info.getPairsGraph().containsEdge(coin2, coin1)) {
                    pair = coin2 + coin1;
                }
                if (coinRatingController.isAnalyzer()) {
                    double coinPairVolume = coinRatingController.getPairBaseHourVolume(pair);
                    if (coinPairVolume > maxPairVolumeForUsingDepsets) {
                        pair = "";
                    }
                }
                if (!pair.isEmpty()) {
                    pairs_usage.put(pair, pairs_usage.getOrDefault(pair, 0) + 1);
                    if (pairs_usage.get(pair) > max_usage) {
                        max_usage = pairs_usage.get(pair);
                    }
                }
            }
        }
        pairs_usage.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()) 
        .limit(limitValue) 
        .forEach((val) -> {
            result.add(val.getKey());
        });
        return result;
    }
    
    private void checkEnterCycles() {
        try {
            info.getSemaphore().acquire();
            List<String> best_cycle = getBestCyclesForEnter();
            if (best_cycle != null) {
                double best_profit = round(getCycleWeight(best_cycle), 4);
                if (best_profit < 1 + 0.01*minProfitPercent) {
                    mainApplication.getInstance().log("", true, false);
                    mainApplication.getInstance().log("Found cycle: " + best_cycle + " but recheck failed!", true, true);
                } else {
                    mainApplication.getInstance().log("", true, false);
                    mainApplication.getInstance().log("Found cycle: " + best_cycle, true, true);
                    mainApplication.getInstance().log("Cycle description: " + getCycleDescription(best_cycle), true, false);
                    mainApplication.getInstance().log("Profit: " + best_profit, true, false);
                    TradeCycleProcess cycleProcess = new TradeCycleProcess(this);
                    cycleProcess.setDelayTime(1);
                    cycleProcess.setProfitAim(best_profit);
                    cycleProcess.addCycleToCycleChain(best_cycle);
                    cycleProcess.setCycleIndex(cycleProcesses.size()+1);
                    cycleProcess.start();
                    cycleProcesses.add(cycleProcess);
                }
            }
        } catch (InterruptedException ex) {
            Logger.getLogger(CoinCycleController.class.getName()).log(Level.SEVERE, null, ex);
        }
        info.getSemaphore().release();
    }
    
    private void checkExitCycles() {
        int from = cycleProcesses.size();
        for(int i = cycleProcesses.size() - 1; i >= 0; i--) {
            if (cycleProcesses.get(i) == null || !cycleProcesses.get(i).isAlive()) {
                cycleProcesses.remove(i);
                from = i;
            }
        }
        for(int i = from; i < cycleProcesses.size(); i++) {
            cycleProcesses.get(i).setCycleIndex(i + 1);
        }
    }
    
    private void updateDepSet() {
        if (!useDepsetUpdates) {
            return;
        }
        if ((System.currentTimeMillis() - lastDepsetSetMillis) < depsetUpdateTime * 1000) {
            return;
        }
        lastDepsetSetMillis = System.currentTimeMillis();
        Set<String> depset = getPairsToDepthCheck(depsetSizeLimit);
        if (depset != null) {
            mainApplication.getInstance().log("Updating depset...");
            mainApplication.getInstance().log("Current set: " + depset);
            info.setDepthCheckForPairSet(depset);
            mainApplication.getInstance().log("Waiting for collect data...");
            doWait(180000);
        }
    }
    
    @Override
    protected void runStart() {
        comissionPercent = client.getTradeComissionPercent().doubleValue();
        mainApplication.getInstance().log("Cycle thread starting...");
        mainApplication.getInstance().log("Cycle main: " + mainCoins + " ("+mainCoins.size()+")");
        mainApplication.getInstance().log("Cycle required: " + requiredCoins + " ("+requiredCoins.size()+")");
        mainApplication.getInstance().log("Cycle restricted: " + restrictCoins + " ("+restrictCoins.size()+")");
        doWait(3000);
    }

    @Override
    protected void runBody() {
        if (cycleProcesses.size() > 0) {
            checkExitCycles();
        }
        if (
            (cycleProcesses.size() < maxActiveCyclesCount) && (
                !coinRatingController.isAnalyzer() ||
                coinRatingController.isHaveAllCoinsPairsInfo()
            )
        ) {
            updateDepSet();
            checkEnterCycles();
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
    public TradePairProcessList getPairProcessController() {
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

    public double getAbortMarketApproxProfit(BigDecimal initialOrderQty, BigDecimal qty, String initialSymbol, String baseSymbol, String quoteSymbol) {
        if (initialOrderQty == null || initialSymbol == null || qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            return -1;
        }
        double currentMainCoinPrice = info.convertSumm(baseSymbol, qty.doubleValue(), initialSymbol);
        double initialPrice = initialOrderQty.doubleValue();
        double mainCoinProfit = (1 + (currentMainCoinPrice - initialPrice) / initialPrice) * ((100.0-comissionPercent) / 100.0);
        System.out.println(initialOrderQty + " " + initialSymbol + " | " + qty + " " + baseSymbol + "/" + quoteSymbol + " | " + currentMainCoinPrice + " " + initialPrice + " " + mainCoinProfit);
        return mainCoinProfit * 0.95;
    }
    
    public SwapInfo doCycleSwap(TradeCycleProcess process) {
        SwapInfo result = null;
        if (process.getCycleStep() >= process.getCycle().size()-1) {
            return result;
        }
        
        List<String> pre_cycle = new ArrayList<>();
        double pre_weight = 1;
        
        for(int i = 0; i < process.getCycleStep(); i++) {
            String csymbol = process.getCycle().get(i);
            boolean isBuy = csymbol.matches("^.* BUY$");
            String pair = csymbol.substring(0, csymbol.indexOf(" ")).trim();
            String coins[] = info.getCoinPairs().get(pair);
            if (isBuy) {
                if (pre_cycle.isEmpty()) pre_cycle.add(coins[1]);
                pre_cycle.add(coins[0]);
            } else {
                if (pre_cycle.isEmpty()) pre_cycle.add(coins[0]);
                pre_cycle.add(coins[1]);
            }
            if (isBuy) {
                pre_weight *= (1/process.getPrices().get(i).doubleValue()) * ((100.0-comissionPercent) / 100.0);
            } else {
                pre_weight *= (process.getPrices().get(i).doubleValue()) * ((100.0-comissionPercent) / 100.0);
            }
        }
        
        System.out.println("Pre cycle: " + pre_cycle);
        System.out.println("Pre weight: " + pre_weight);
        
        if (pre_cycle.isEmpty()) {
            return result;
        }
        
        try {
            info.getSemaphore().acquire();
            List<String> cycle = getBestCyclesForEnter(pre_cycle, pre_weight);
            if (cycle != null && !cycle.isEmpty()) {
                double new_weight = getCycleWeight(cycle, pre_cycle.size(), pre_weight);
                if (new_weight < 1 + 0.01*minProfitPercent) {
                    mainApplication.getInstance().log("", true, false);
                    mainApplication.getInstance().log("Found cycle: " + cycle + " but recheck failed!", true, true);
                } else {
                    mainApplication.getInstance().log("", true, false);
                    mainApplication.getInstance().log("Found cycle: " + cycle, true, true);
                    mainApplication.getInstance().log("Cycle description: " + getCycleDescription(cycle), true, false);
                    mainApplication.getInstance().log("Profit: " + new_weight, true, false);

                    result = new SwapInfo();
                    result.cycle = cycle;
                    result.new_weight = new_weight;
                    result.pre_size = pre_cycle.size();
                }
            }
        
        } catch (InterruptedException ex) {
            Logger.getLogger(CoinCycleController.class.getName()).log(Level.SEVERE, null, ex);
        }
        info.getSemaphore().release();

        return result;
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
        if (!restrictCoins.trim().isEmpty()) {
            this.restrictCoins = new HashSet<>(Arrays.asList(restrictCoins.toUpperCase().trim().split(",")));
        } else {
            this.restrictCoins = new HashSet<>();
        }
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
        if (!requiredCoins.trim().isEmpty()) {
            this.requiredCoins = new HashSet<>(Arrays.asList(requiredCoins.toUpperCase().trim().split(",")));
        } else {
            this.requiredCoins = new HashSet<>();
        }
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
        if (!mainCoins.trim().isEmpty()) {
            this.mainCoins = new HashSet<>(Arrays.asList(mainCoins.toUpperCase().trim().split(",")));
        } else {
            this.mainCoins = new HashSet<>();
        }
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

    /**
     * @return the stopFirstLimitTimeout
     */
    public int getStopFirstLimitTimeout() {
        return stopFirstLimitTimeout;
    }

    /**
     * @param stopFirstLimitTimeout the stopFirstLimitTimeout to set
     */
    public void setStopFirstLimitTimeout(int stopFirstLimitTimeout) {
        this.stopFirstLimitTimeout = stopFirstLimitTimeout;
    }

    /**
     * @return the maxSwitchesCount
     */
    public int getMaxSwitchesCount() {
        return maxSwitchesCount;
    }

    /**
     * @param maxSwitchesCount the maxSwitchesCount to set
     */
    public void setMaxSwitchesCount(int maxSwitchesCount) {
        this.maxSwitchesCount = maxSwitchesCount;
    }

    /**
     * @return the maxActiveCyclesCount
     */
    public int getMaxActiveCyclesCount() {
        return maxActiveCyclesCount;
    }

    /**
     * @param maxActiveCyclesCount the maxActiveCyclesCount to set
     */
    public void setMaxActiveCyclesCount(int maxActiveCyclesCount) {
        this.maxActiveCyclesCount = maxActiveCyclesCount;
    }

    /**
     * @return the minCycleLength
     */
    public int getMinCycleLength() {
        return minCycleLength;
    }

    /**
     * @param minCycleLength the minCycleLength to set
     */
    public void setMinCycleLength(int minCycleLength) {
        this.minCycleLength = minCycleLength;
    }

    /**
     * @return the maxCycleLength
     */
    public int getMaxCycleLength() {
        return maxCycleLength;
    }

    /**
     * @param maxCycleLength the maxCycleLength to set
     */
    public void setMaxCycleLength(int maxCycleLength) {
        this.maxCycleLength = maxCycleLength;
    }

    /**
     * @return the useDepsetUpdates
     */
    public boolean isUseDepsetUpdates() {
        return useDepsetUpdates;
    }

    /**
     * @param useDepsetUpdates the useDepsetUpdates to set
     */
    public void setUseDepsetUpdates(boolean useDepsetUpdates) {
        this.useDepsetUpdates = useDepsetUpdates;
    }
}
