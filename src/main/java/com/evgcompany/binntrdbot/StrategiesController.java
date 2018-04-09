/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import com.evgcompany.binntrdbot.analysis.*;
import com.evgcompany.binntrdbot.strategies.*;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.stream.Collectors;
import org.ta4j.core.*;
import org.ta4j.core.analysis.criteria.*;
import org.ta4j.core.indicators.helpers.*;
import org.ta4j.core.trading.rules.*;

/**
 *
 * @author EVG_adm_T
 */
public class StrategiesController {
    
    public class StrategyMarker {
        String label = "";
        double timeStamp = 0;
        double value = 0;
        int typeIndex = 0;
    }

    public enum StrategiesMode {
       NORMAL, BUY_DIP, SELL_UP
    }
    public enum StrategiesAction {
       DO_NOTHING, DO_ENTER, DO_LEAVE
    }
    
    private mainApplication app = null;
    private tradeProfitsController profitsChecker = null;
    private TimeSeries series = null;
    private final HashMap<String, Strategy> strategies = new HashMap<>();
    private final List<StrategyItem> strategy_items = new ArrayList<>();
    
    private List<String> strategy_auto = new ArrayList<>();
    private boolean autoWalkForward = false;
    
    private String mainStrategy = "Auto";
    private int strategyItemIndex = -1;
    private int barSeconds;
    private String groupName;
    private final List<StrategyMarker> markers = new ArrayList<>();
    private TradingRecord tradingRecord = null;
    
    private static final DecimalFormat df6 = new DecimalFormat("0.######");
    
    public StrategiesController(String groupName, mainApplication app, tradeProfitsController profitsChecker) {
        this.groupName = groupName;
        this.app = app;
        this.profitsChecker = profitsChecker;
        this.tradingRecord = new BaseTradingRecord();
    }
    public StrategiesController() {
        this.groupName = null;
        this.app = null;
        this.profitsChecker = null;
        this.tradingRecord = null;
    }
    
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }
    
    public void setBarSeconds(int barSeconds) {
        this.barSeconds = barSeconds;
    }
    
    public TimeSeries getSeries() {
        return series;
    }
    
    public List<StrategyMarker> getStrategyMarkers() {
        return markers;
    }
    
    public tradeProfitsController getProfitsChecker() {
        return profitsChecker;
    }
    
    /**
     * @return the mainStrategy
     */
    public String getMainStrategy() {
        return mainStrategy;
    }

    public Strategy getMainStrategyS() {
        return strategies.get(mainStrategy);
    }
    
    public boolean isShouldLeave() {
        Strategy ms = getMainStrategyS();
        if (ms != null) {
            return ms.shouldExit(series.getEndIndex());
        }
        return false;
    }

    public boolean isShouldEnter() {
        Strategy ms = getMainStrategyS();
        if (ms != null) {
            return ms.shouldEnter(series.getEndIndex());
        }
        return false;
    }
    
    /**
     * @param mainStrategy the mainStrategy to set
     */
    public void setMainStrategy(String mainStrategy) {
        this.mainStrategy = mainStrategy;
    }
    
    public void addStrategyMarker(boolean is_enter, String strategy_name) {
        if (profitsChecker != null) {
            StrategyMarker newm = new StrategyMarker();
            newm.label = (is_enter ? "Enter" : "Exit") + " (" + strategy_name + ")";
            newm.timeStamp = profitsChecker.getClient().getAlignedCurrentTimeMillis();
            if (strategy_name.equals(mainStrategy)) {
                newm.typeIndex = is_enter ? 0 : 1;
            } else {
                newm.typeIndex = is_enter ? 2 : 3;
            }
            markers.add(newm);
        }
    }
    
    private void addStrategyPastMarker(boolean is_enter, long timestamp, String strategy_name) {
        StrategyMarker newm = new StrategyMarker();
        newm.label = (is_enter ? "Enter" : "Exit") + " (" + strategy_name + ")";
        newm.timeStamp = timestamp;
        newm.typeIndex = is_enter ? 0 : 1;
        markers.add(newm);
    }
    
    public TimeSeries resetSeries() {
        series = new BaseTimeSeries(groupName + "_SERIES");
        return series;
    }
    
    private Rule addStopLossGain(Rule rule, TimeSeries series) {
        if (profitsChecker != null) {
            /*if (profitsChecker.isLowHold()) {
                rule = rule.and(new StopGainRule(new ClosePriceIndicator(series), Decimal.valueOf(profitsChecker.getTradeMinProfitPercent())));
            }*/
            if (profitsChecker.getStopLossPercent() != null) {
                rule = rule.or(new StopLossRule(new ClosePriceIndicator(series), Decimal.valueOf(profitsChecker.getStopLossPercent())));
            }
            if (profitsChecker.getStopGainPercent() != null) {
                rule = rule.or(new StopGainRule(new ClosePriceIndicator(series), Decimal.valueOf(profitsChecker.getStopGainPercent())));
            }
        }
        return rule;
    }
    
    public void logStatistics(boolean init_marks) {
        
        if (app == null) return;
        
        TimeSeriesManager seriesManager = new TimeSeriesManager(series);
        TradingRecord tradingRecordC = seriesManager.run(strategies.get(mainStrategy));
        if (init_marks) {
            List<org.ta4j.core.Trade> trlist = tradingRecordC.getTrades();
            for(int k=0; k<trlist.size(); k++) {
                int index;
                org.ta4j.core.Trade rtrade = trlist.get(k);
                if (rtrade.getEntry() != null) {
                    index = rtrade.getEntry().getIndex();
                    addStrategyPastMarker(true, series.getBar(index).getBeginTime().toInstant().toEpochMilli(), mainStrategy);
                }
                if (rtrade.getExit() != null) {
                    index = rtrade.getExit().getIndex();
                    addStrategyPastMarker(false, series.getBar(index).getEndTime().toInstant().toEpochMilli(), mainStrategy);
                }
            }
        }

        StrategyItem main = getMainStrategyItem();
        if (main != null) {
            app.log("");
            app.log("Current params for " + main.getStrategyName() + " is: " + main.getConfig().toString(true));
        }

        /**
         * Analysis criteria
         */
        app.log("");
        app.log("Current "+groupName+" strategy ("+mainStrategy+") criterias:");
        TotalProfitCriterion totalProfit = new TotalProfitCriterion();
        app.log("Total profit: " + totalProfit.calculate(series, tradingRecordC));
        if (profitsChecker != null)
            app.log("Total transaction cost: " + new LinearTransactionCostCriterion(1, 0.01f * profitsChecker.getTradeComissionPercent().doubleValue()).calculate(series, tradingRecordC));
        app.log("Average profit (per tick): " + new AverageProfitCriterion().calculate(series, tradingRecordC));
        app.log("Maximum drawdown: " + new MaximumDrawdownCriterion().calculate(series, tradingRecordC));
        app.log("Reward-risk ratio: " + new RewardRiskRatioCriterion().calculate(series, tradingRecordC));
        app.log("Buy-and-hold: " + new BuyAndHoldCriterion().calculate(series, tradingRecordC));
        app.log("");
        app.log("Number of trades: " + new NumberOfTradesCriterion().calculate(series, tradingRecordC));
        app.log("Profitable trades ratio: " + new AverageProfitableTradesCriterion().calculate(series, tradingRecordC));
        if (profitsChecker != null)
            app.log("Profit without comission: " + new ProfitWithoutComissionCriterion(0.01f * profitsChecker.getTradeComissionPercent().doubleValue()).calculate(series, tradingRecordC));
        app.log("Custom strategy profit vs buy-and-hold strategy profit: " + new VersusBuyAndHoldCriterion(totalProfit).calculate(series, tradingRecordC));
        app.log("");app.log("");
    }
    
    public void resetStrategies() {
        boolean need_main_optimize = false;
        markers.clear();
        strategies.clear();
        getStrategiesInitializerMap();
        strategy_items.forEach((entry) -> {
            Strategy nstrategy;
            if (
                    mainStrategy.equals(entry.getStrategyName()) && 
                    profitsChecker != null && 
                    profitsChecker.isAutoPickStrategyParams()
            ) {
                nstrategy = entry.buildStrategyWithBestParams(series);
            } else {
                nstrategy = entry.buildStrategy(series);
            }
            strategies.put(entry.getStrategyName(), nstrategy);
        });
        if (profitsChecker != null) {
            strategy_auto = profitsChecker.getAutoStrategies();
            autoWalkForward = profitsChecker.isAutoWalkForward();
        }
        if (mainStrategy.equals("Auto")) {
            mainStrategy = findOptimalStrategy();
            if (app != null) app.log("Optimal strategy for " + groupName + " is " + mainStrategy, true, false);
            need_main_optimize = profitsChecker != null && profitsChecker.isAutoPickStrategyParams();
        }
        if (!strategies.containsKey(mainStrategy)) {
            List<String> keysAsArray = new ArrayList<>(strategies.keySet());
            Random r = new Random();
            mainStrategy = keysAsArray.get(r.nextInt(keysAsArray.size()));
            if (app != null) app.log("Strategy for " + groupName + " is not found. Using random = " + mainStrategy, true, false);
        }
        strategyItemIndex = -1;
        for(int i=0; i < strategy_items.size(); i++) {
            if (strategy_items.get(i).getStrategyName().equals(mainStrategy)) {
                strategyItemIndex = i;
            }
        }

        if (need_main_optimize) {
            StrategyItem main = getMainStrategyItem();
            if (main != null) {
                app.log("Looking for best params for strategy: " + main.getStrategyName() + "...");
                strategies.put(main.getStrategyName(), main.buildStrategyWithBestParams(series));
            }
        }

        logStatistics(true);
    }
    
    public double getStrategiesEnterRate(int timeframe) {
        double result = 0;
        int start_index = series.getEndIndex() - timeframe;
        if (start_index < 0) start_index = 0;
        int end_index = series.getEndIndex();
        List<Strategy> pick_list = getAutoListStrategies();
        for (Strategy strategy : pick_list) {
            double k = 1;
            for(int i=end_index;i>=start_index;i--) {
                if (strategy.shouldEnter(i)) {
                    result+=k;
                    i = end_index+1;
                }
                k *= 0.825;
            }
        }
        return result;
    }
    public double getStrategiesExitRate(int timeframe) {
        TradingRecord tradingRecordT = new BaseTradingRecord();
        tradingRecordT.enter(0);
        double result = 0;
        int start_index = series.getEndIndex() - timeframe;
        if (start_index < 0) start_index = 0;
        int end_index = series.getEndIndex();
        List<Strategy> pick_list = getAutoListStrategies();
        for (Strategy strategy : pick_list) {
            double k = 1;
            for(int i=end_index;i>=start_index;i--) {
                if (strategy.shouldExit(i, tradingRecordT)) {
                    result+=k;
                    i = end_index+1;
                }
                k *= 0.825;
            }
        }
        return result;
    }

    private List<String> getAutoListString() {
        if ((strategy_auto == null || strategy_auto.isEmpty()) && profitsChecker != null) {
            strategy_auto = profitsChecker.getAutoStrategies();
            autoWalkForward = profitsChecker.isAutoWalkForward();
        }
        if (strategy_auto != null && !strategy_auto.isEmpty()) {
            return strategy_auto;
        }
        return new ArrayList<>(strategies.keySet());
    }

    private List<Strategy> getAutoListStrategies() {
        if ((strategy_auto == null || strategy_auto.isEmpty()) && profitsChecker != null) {
            strategy_auto = profitsChecker.getAutoStrategies();
            autoWalkForward = profitsChecker.isAutoWalkForward();
        }
        if (strategy_auto != null && !strategy_auto.isEmpty()) {
            List<Strategy> slist = new ArrayList<>();
            strategy_auto.forEach((entry) -> {
                slist.add(strategies.get(entry));
            });
            return slist;
        }
        return new ArrayList<>(strategies.values());
    }
    
    private String getSeriesPeriodDescription(TimeSeries series) {
        StringBuilder sb = new StringBuilder();
        if (!series.getBarData().isEmpty()) {
            Bar firstBar = series.getFirstBar();
            Bar lastBar = series.getLastBar();
            sb.append(firstBar.getEndTime().format(DateTimeFormatter.ofPattern("dd.MM.yyyy kk:mm:ss")))
                    .append(" - ")
                    .append(lastBar.getEndTime().format(DateTimeFormatter.ofPattern("dd.MM.yyyy kk:mm:ss")));
        }
        return sb.toString();
    }
    
    private String getStrategyName(Strategy strategy, String default_name) {
        for (Map.Entry<String, Strategy> entry : strategies.entrySet()) {
            if (Objects.equals(strategy, entry.getValue())) {
                return entry.getKey();
            }
        }
        return default_name;
    }
    
    private String findOptimalStrategy() {
        String result = "Unknown";
        List<String> auto_list = getAutoListString();
        List<Strategy> pick_list = getAutoListStrategies();
        List<TimeSeries> subseries;
        
        if (autoWalkForward) {
            subseries = splitSeries(
                series, 
                Duration.ofSeconds(2 * 60 * 60), 
                Duration.ofSeconds(24 * 60 * 60)
            );
        } else {
            subseries = new ArrayList<>();
            subseries.add(series);
        }

        AnalysisCriterion profitCriterion;
        if (profitsChecker != null)
            profitCriterion = new ProfitWithoutComissionCriterion(0.01f * profitsChecker.getTradeComissionPercent().doubleValue());
        else 
            profitCriterion = new TotalProfitCriterion();
        Map<String, Double> successMap = new HashMap<>();
        
        String log = "";
        
        for (TimeSeries slice : subseries) {
            log += "Sub-series: " + getSeriesPeriodDescription(slice) + "\n";
            TimeSeriesManager sliceManager = new TimeSeriesManager(slice);
            for (String strategy_key : auto_list) {
                Strategy strategy = strategies.get(strategy_key);
                TradingRecord tradingRecordC = sliceManager.run(strategy);
                double profit = profitCriterion.calculate(slice, tradingRecordC);
                log += "    Profit wo comission for " + strategy_key + ": " + df6.format(profit) + "\n";
            }
            Strategy bestStrategy = profitCriterion.chooseBest(sliceManager, pick_list);
            String bestStrategyName = getStrategyName(bestStrategy, result);
            log += "\t--> Best strategy: " + bestStrategyName + "\n\n";
            
            TradingRecord tradingRecordC = sliceManager.run(bestStrategy);
            if (tradingRecordC.getTradeCount() > 0) {
                if (!successMap.containsKey(bestStrategyName)) successMap.put(bestStrategyName, 0.0);
                successMap.put(bestStrategyName, successMap.get(bestStrategyName) + 1);
            }
        }

        double max_value = 0;
        for (Map.Entry<String, Double> entry : successMap.entrySet()) {
            if (entry.getValue() > max_value) {
                max_value = entry.getValue();
                result = entry.getKey();
            }
        }

        if (subseries.size() > 1) {
            log += "\nSummary ("+subseries.size()+" slices):\n";
            for (Map.Entry<String, Double> entry : successMap.entrySet()) {
                log += "    Wins count for " + entry.getKey() + ": " + df6.format(entry.getValue()) + "\n";
            }
            log += "\t--> Summ Best strategy: " + result + "\n\n";
        }
        
        if (app != null) app.log(log);
        
        return result;
    }
    
    /**
     * Builds a list of split indexes from splitDuration.
     * @param series the time series to get split begin indexes of
     * @param splitDuration the duration between 2 splits
     * @return a list of begin indexes after split
     */
    private static List<Integer> getSplitBeginIndexes(TimeSeries series, Duration splitDuration) {
        ArrayList<Integer> beginIndexes = new ArrayList<>();

        int beginIndex = series.getBeginIndex();
        int endIndex = series.getEndIndex();
        
        // Adding the first begin index
        beginIndexes.add(beginIndex);

        // Building the first interval before next split
        ZonedDateTime beginInterval = series.getFirstBar().getEndTime();
        ZonedDateTime endInterval = beginInterval.plus(splitDuration);

        for (int i = beginIndex; i <= endIndex; i++) {
            // For each tick...
            ZonedDateTime tickTime = series.getBar(i).getEndTime();
            if (tickTime.isBefore(beginInterval) || !tickTime.isBefore(endInterval)) {
                // Tick out of the interval
                if (!endInterval.isAfter(tickTime)) {
                    // Tick after the interval
                    // --> Adding a new begin index
                    beginIndexes.add(i);
                }

                // Building the new interval before next split
                beginInterval = endInterval.isBefore(tickTime) ? tickTime : endInterval;
                endInterval = beginInterval.plus(splitDuration);
            }
        }
        return beginIndexes;
    }
    
    /**
     * Returns a new time series which is a view of a subset of the current series.
     * <p>
     * The new series has begin and end indexes which correspond to the bounds of the sub-set into the full series.<br>
     * The tick of the series are shared between the original time series and the returned one (i.e. no copy).
     * @param series the time series to get a sub-series of
     * @param beginIndex the begin index (inclusive) of the time series
     * @param duration the duration of the time series
     * @return a constrained {@link TimeSeries time series} which is a sub-set of the current series
     */
    private static TimeSeries subseries(TimeSeries series, int beginIndex, Duration duration) {

        // Calculating the sub-series interval
        ZonedDateTime beginInterval = series.getBar(beginIndex).getEndTime();
        ZonedDateTime endInterval = beginInterval.plus(duration);

        // Checking ticks belonging to the sub-series (starting at the provided index)
        int subseriesNbTicks = 0;
        int endIndex = series.getEndIndex();
        for (int i = beginIndex; i <= endIndex; i++) {
            // For each tick...
            ZonedDateTime tickTime = series.getBar(i).getEndTime();
            if (tickTime.isBefore(beginInterval) || !tickTime.isBefore(endInterval)) {
                // Tick out of the interval
                break;
            }
            // Tick in the interval
            // --> Incrementing the number of ticks in the subseries
            subseriesNbTicks++;
        }

        return new BaseTimeSeries(series, beginIndex, beginIndex + subseriesNbTicks - 1);
    }

    /**
     * Splits the time series into sub-series lasting sliceDuration.<br>
     * The current time series is splitted every splitDuration.<br>
     * The last sub-series may last less than sliceDuration.
     * @param series the time series to split
     * @param splitDuration the duration between 2 splits
     * @param sliceDuration the duration of each sub-series
     * @return a list of sub-series
     */
    private static List<TimeSeries> splitSeries(TimeSeries series, Duration splitDuration, Duration sliceDuration) {
        ArrayList<TimeSeries> subseries = new ArrayList<>();
        if (splitDuration != null && !splitDuration.isZero() && 
            sliceDuration != null && !sliceDuration.isZero()) {

            List<Integer> beginIndexes = getSplitBeginIndexes(series, splitDuration);
            beginIndexes.forEach((subseriesBegin) -> {
                subseries.add(subseries(series, subseriesBegin, sliceDuration));
            });
        }
        return subseries;
    }
    
    public StrategiesAction checkStatus(boolean is_hodling, boolean checkOtherStrategies, StrategiesMode mode) {
        int endIndex = series.getEndIndex();
        
        if (is_hodling) {
            if (mode != StrategiesMode.BUY_DIP) {
                if (strategies.get(mainStrategy).shouldExit(endIndex, tradingRecord)) {
                    addStrategyMarker(false, mainStrategy);
                    return StrategiesAction.DO_LEAVE;
                } else if (strategies.get(mainStrategy).shouldEnter(endIndex, tradingRecord)) {
                    addStrategyMarker(true, mainStrategy);
                    if (app != null) app.log(groupName + " should enter here but already hodling...", false, true);
                }
            }
        } else {
            if (strategies.get(mainStrategy).shouldEnter(endIndex, tradingRecord)) {
                addStrategyMarker(true, mainStrategy);
                return StrategiesAction.DO_ENTER;
            } else if (strategies.get(mainStrategy).shouldExit(endIndex, tradingRecord)) {
                addStrategyMarker(false, mainStrategy);
                if (app != null) app.log(groupName + " should exit here but everything is sold...", false, true);
            }
        }

        if (checkOtherStrategies && mode != StrategiesMode.BUY_DIP) {
            for (Map.Entry<String, Strategy> entry : strategies.entrySet()) {
                if (!entry.getKey().equals(mainStrategy)) {
                    if (is_hodling) {
                        if (entry.getValue().shouldExit(endIndex, tradingRecord)) {
                            addStrategyMarker(false, entry.getKey());
                            if (app != null) app.log(groupName + " should exit here ("+entry.getKey()+")...", false, true);
                            if (mode != StrategiesMode.SELL_UP) {
                                return StrategiesAction.DO_LEAVE;
                            }
                        } else if (entry.getValue().shouldEnter(endIndex, tradingRecord)) {
                            addStrategyMarker(true, entry.getKey());
                            if (app != null) app.log(groupName + " should enter here ("+entry.getKey()+") but already hodling...", false, true);
                        }
                    } else {
                        if (entry.getValue().shouldEnter(endIndex, tradingRecord)) {
                            addStrategyMarker(true, entry.getKey());
                            if (app != null) app.log(groupName + " should enter here ("+entry.getKey()+")...", false, true);
                        } else if (entry.getValue().shouldExit(endIndex, tradingRecord)) {
                            addStrategyMarker(false, entry.getKey());
                            if (app != null) app.log(groupName + " should exit here ("+entry.getKey()+") but everything is sold...", false, true);
                        }
                    }
                }
            }
        }
        return StrategiesAction.DO_NOTHING;
    }

    public StrategyItem getMainStrategyItem() {
        return strategyItemIndex>=0 ? strategy_items.get(strategyItemIndex) : null;
    }
    
    public List<StrategyItem> getStrategiesInitializerMap() {
        strategy_items.clear();
        strategy_items.add(new StrategyNo(this));
        strategy_items.add(new StrategyNeuralNetwork(this));  // @todo
        strategy_items.add(new StrategyANN(this));
        strategy_items.add(new StrategySMA(this));
        strategy_items.add(new StrategyEMA(this));
        strategy_items.add(new StrategyThreeSoldiers(this));
        strategy_items.add(new StrategyMovingMomentum(this));
        strategy_items.add(new StrategyCCICorrection(this));
        strategy_items.add(new StrategyGlobalExtrema(this));
        strategy_items.add(new StrategyAdvancedEMA(this));
        strategy_items.add(new StrategyRSI2(this));
        strategy_items.add(new StrategyCMO(this));
        strategy_items.add(new StrategyKAMA(this));
        strategy_items.add(new StrategyAroon(this));
        strategy_items.add(new StrategyKeltnerChannel(this));
        strategy_items.add(new StrategyBearishEngulfingEMA(this));
        strategy_items.add(new StrategyChaikinMoneyFlow(this));
        strategy_items.add(new StrategyVWAP(this));
        strategy_items.add(new StrategyWIP(this));
        strategy_items.add(new StrategyIchimoku(this, 1));
        strategy_items.add(new StrategyIchimoku(this, 2));
        strategy_items.add(new StrategyIchimoku(this, 3));
        strategy_items.add(new StrategyBollinger(this, 1));
        strategy_items.add(new StrategyBollinger(this, 2));
        strategy_items.add(new StrategyBollinger(this, 3));
        strategy_items.add(new StrategyFractalBreakout(this));
        strategy_items.add(new StrategyParabolicSAR(this));
        return strategy_items;
    }

    public List<String> getStrategiesNames() {
        if (strategy_items.isEmpty()) {
            getStrategiesInitializerMap();
        }
        return strategy_items.stream().map((item) -> {
            return item.getStrategyName();
        }).collect(Collectors.toList());
    }

    /**
     * @return the tradingRecord
     */
    public TradingRecord getTradingRecord() {
        return tradingRecord;
    }

    /**
     * @param tradingRecord the tradingRecord to set
     */
    public void setTradingRecord(TradingRecord tradingRecord) {
        this.tradingRecord = tradingRecord;
    }

    /**
     * @param profitsChecker the profitsChecker to set
     */
    public void setProfitsChecker(tradeProfitsController profitsChecker) {
        this.profitsChecker = profitsChecker;
    }
}
