/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import com.binance.api.client.SyncedTime;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.Decimal;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.TimeSeriesManager;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.criteria.AverageProfitCriterion;
import org.ta4j.core.analysis.criteria.AverageProfitableTradesCriterion;
import org.ta4j.core.analysis.criteria.BuyAndHoldCriterion;
import org.ta4j.core.analysis.criteria.LinearTransactionCostCriterion;
import org.ta4j.core.analysis.criteria.MaximumDrawdownCriterion;
import org.ta4j.core.analysis.criteria.NumberOfTradesCriterion;
import org.ta4j.core.analysis.criteria.RewardRiskRatioCriterion;
import org.ta4j.core.analysis.criteria.TotalProfitCriterion;
import org.ta4j.core.analysis.criteria.VersusBuyAndHoldCriterion;
import org.ta4j.core.indicators.CCIIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.StochasticOscillatorKIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.indicators.helpers.MaxPriceIndicator;
import org.ta4j.core.indicators.helpers.MinPriceIndicator;
import org.ta4j.core.indicators.helpers.MultiplierIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuChikouSpanIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuKijunSenIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanAIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanBIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuTenkanSenIndicator;
import org.ta4j.core.trading.rules.BooleanRule;
import org.ta4j.core.trading.rules.CrossedDownIndicatorRule;
import org.ta4j.core.trading.rules.CrossedUpIndicatorRule;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;

class StrategyMarker {
    String label = "";
    double timeStamp = 0;
    int typeIndex = 0;
}

enum StrategiesMode {
   NORMAL, BUY_DIP, SELL_UP
}
enum StrategiesAction {
   DO_NOTHING, DO_ENTER, DO_LEAVE
}

/**
 *
 * @author EVG_adm_T
 */
public class StrategiesController {
    private mainApplication app = null;
    private tradeProfitsController profitsChecker = null;
    private TimeSeries series = null;
    HashMap<String, Strategy> strategies = new HashMap<String, Strategy>();
    private String mainStrategy = "Auto";
    private int barSeconds;
    private String groupName;
    private List<StrategyMarker> markers = new ArrayList<>();
    
    public StrategiesController(String groupName, mainApplication app, tradeProfitsController profitsChecker) {
        this.groupName = groupName;
        this.app = app;
        this.profitsChecker = profitsChecker;
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
    
    /**
     * @return the mainStrategy
     */
    public String getMainStrategy() {
        return mainStrategy;
    }

    /**
     * @param mainStrategy the mainStrategy to set
     */
    public void setMainStrategy(String mainStrategy) {
        this.mainStrategy = mainStrategy;
    }
    
    public void addStrategyMarker(boolean is_enter, String strategy_name) {
        StrategyMarker newm = new StrategyMarker();
        newm.label = (is_enter ? "Enter" : "Exit") + " (" + strategy_name + ")";
        newm.timeStamp = SyncedTime.getInstance(-1).currentTimeMillis();
        if (strategy_name.equals(mainStrategy)) {
            newm.typeIndex = is_enter ? 0 : 1;
        } else {
            newm.typeIndex = is_enter ? 2 : 3;
        }
        markers.add(newm);
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
    
    /**
     * @param series a time series
     * @return a dummy strategy
     */
    private Strategy buildNoStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        return new BaseStrategy(
                new BooleanRule(false),
                new BooleanRule(false)
        );
    }
    
    /**
     * @param series a time series
     * @return a dummy strategy
     */
    private Strategy buildSimpleStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator sma = new SMAIndicator(closePrice, 12);

        // Signals
        // Buy when SMA goes over close price
        // Sell when close price goes over SMA
        return new BaseStrategy(
                new OverIndicatorRule(sma, closePrice),
                new UnderIndicatorRule(sma, closePrice)
        );
    }

    
    /**
     * @param series a time series
     * @return a AdvancedEMA strategy
     */
    private Strategy buildAdvancedEMAStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        EMAIndicator ema_y = new EMAIndicator(closePrice, 55);
        EMAIndicator ema_1 = new EMAIndicator(closePrice, 21);
        EMAIndicator ema_2 = new EMAIndicator(closePrice, 13);
        EMAIndicator ema_3 = new EMAIndicator(closePrice, 8);
        
        // Entry rule
        Rule entryRule = new UnderIndicatorRule(ema_y, ema_1)
                .and(new UnderIndicatorRule(ema_y, ema_2))
                .and(new UnderIndicatorRule(ema_y, ema_3));
        
        // Exit rule
        Rule exitRule = new OverIndicatorRule(ema_y, ema_1)
                .and(new OverIndicatorRule(ema_y, ema_2))
                .and(new OverIndicatorRule(ema_y, ema_3));
        
        return new BaseStrategy(entryRule, exitRule);
    }
    
    
    /**
     * @param series a time series
     * @return a Ichimoku strategy
     */
    private Strategy buildIchimokuStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        IchimokuTenkanSenIndicator tenkanSen = new IchimokuTenkanSenIndicator(series, 3);
        IchimokuKijunSenIndicator kijunSen = new IchimokuKijunSenIndicator(series, 5);
        IchimokuSenkouSpanAIndicator senkouSpanA = new IchimokuSenkouSpanAIndicator(series, tenkanSen, kijunSen);
        IchimokuSenkouSpanBIndicator senkouSpanB = new IchimokuSenkouSpanBIndicator(series, 9);
        IchimokuChikouSpanIndicator chikouSpan = new IchimokuChikouSpanIndicator(series, 5);
        
        // Entry rule
        Rule entryRule = new CrossedDownIndicatorRule(kijunSen, tenkanSen);
        
        // Exit rule
        Rule exitRule = new CrossedUpIndicatorRule(kijunSen, tenkanSen);
        
        return new BaseStrategy(entryRule, exitRule);
    }
    
    /**
     * @param series a time series
     * @return a Ichimoku strategy
     */
    private Strategy buildIchimoku2Strategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        IchimokuTenkanSenIndicator tenkanSen = new IchimokuTenkanSenIndicator(series, 3);
        IchimokuKijunSenIndicator kijunSen = new IchimokuKijunSenIndicator(series, 5);
        IchimokuSenkouSpanAIndicator senkouSpanA = new IchimokuSenkouSpanAIndicator(series, tenkanSen, kijunSen);
        IchimokuSenkouSpanBIndicator senkouSpanB = new IchimokuSenkouSpanBIndicator(series, 9);
        IchimokuChikouSpanIndicator chikouSpan = new IchimokuChikouSpanIndicator(series, 5);
        
        // Entry rule
        Rule entryRule = new CrossedDownIndicatorRule(closePrice, kijunSen);
        
        // Exit rule
        Rule exitRule = new CrossedUpIndicatorRule(closePrice, kijunSen);
        
        return new BaseStrategy(entryRule, exitRule);
    }
    
    /**
     * @param series a time series
     * @return a Ichimoku strategy
     */
    private Strategy buildIchimoku3Strategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        IchimokuTenkanSenIndicator tenkanSen = new IchimokuTenkanSenIndicator(series, 3);
        IchimokuKijunSenIndicator kijunSen = new IchimokuKijunSenIndicator(series, 5);
        IchimokuSenkouSpanAIndicator senkouSpanA = new IchimokuSenkouSpanAIndicator(series, tenkanSen, kijunSen);
        IchimokuSenkouSpanBIndicator senkouSpanB = new IchimokuSenkouSpanBIndicator(series, 9);
        IchimokuChikouSpanIndicator chikouSpan = new IchimokuChikouSpanIndicator(series, 5);
        
        // Entry rule
        Rule entryRule = new CrossedDownIndicatorRule(closePrice, senkouSpanB);
        
        // Exit rule
        Rule exitRule = new CrossedUpIndicatorRule(closePrice, senkouSpanB);
        
        return new BaseStrategy(entryRule, exitRule);
    }
    
    /**
     * @param series a time series
     * @return a dummy strategy
     */
    private Strategy buildMyMainStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        // http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud
        /*IchimokuTenkanSenIndicator tenkanSen = new IchimokuTenkanSenIndicator(series, 3);
        IchimokuKijunSenIndicator kijunSen = new IchimokuKijunSenIndicator(series, 5);
        IchimokuSenkouSpanAIndicator senkouSpanA = new IchimokuSenkouSpanAIndicator(series, tenkanSen, kijunSen);
        IchimokuSenkouSpanBIndicator senkouSpanB = new IchimokuSenkouSpanBIndicator(series, 9);
        IchimokuChikouSpanIndicator chikouSpan = new IchimokuChikouSpanIndicator(series, 5);*/
        
        // http://www.tradingsystemlab.com/files/The%20Fisher%20Transform.pdf
        // FisherIndicator fisher = new FisherIndicator(series);

        // https://alanhull.com/hull-moving-average
        // HMAIndicator hma = new HMAIndicator(new ClosePriceIndicator(series), 9);
        
        // http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:kaufman_s_adaptive_moving_average
        // KAMAIndicator kama = new KAMAIndicator(new ClosePriceIndicator(series), 10, 2, 30);
        
        // http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:aroon
        // AroonUpIndicator arronUp = new AroonUpIndicator(series, 5);
        
        
        
        
        
        // https://blokt.com/technical-analysis/cryptocurrency-indicators
        // https://hackernoon.com/my-top-3-favourite-indicators-for-technical-analysis-of-cryptocurrencies-b552f584776d
        
        
        
        
        
        
        //OpenPriceIndicator openPriceIndicator = new OpenPriceIndicator(series);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        
        // The bias is bullish when the shorter-moving average moves above the longer moving average.
        // The bias is bearish when the shorter-moving average moves below the longer moving average.
        EMAIndicator shortEma = new EMAIndicator(closePrice, 7);
        EMAIndicator longEma = new EMAIndicator(closePrice, 25);

        MACDIndicator macd = new MACDIndicator(closePrice, 9, 26);
        EMAIndicator emaMacd = new EMAIndicator(macd, 18);
        
        // Entry rule
        Rule entryRule = new OverIndicatorRule(shortEma, longEma)
                .and(new OverIndicatorRule(macd, emaMacd));
        
        // Exit rule
        Rule exitRule = new UnderIndicatorRule(shortEma, longEma)
                .and(new UnderIndicatorRule(macd, emaMacd));
        
        return new BaseStrategy(entryRule, exitRule);
    }
    
    /**
     * @param series a time series
     * @return a moving momentum strategy
     */
    public Strategy buildMovingMomentumStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        
        // The bias is bullish when the shorter-moving average moves above the longer moving average.
        // The bias is bearish when the shorter-moving average moves below the longer moving average.
        EMAIndicator shortEma = new EMAIndicator(closePrice, 9);
        EMAIndicator longEma = new EMAIndicator(closePrice, 26);

        StochasticOscillatorKIndicator stochasticOscillK = new StochasticOscillatorKIndicator(series, 14);

        MACDIndicator macd = new MACDIndicator(closePrice, 9, 26);
        EMAIndicator emaMacd = new EMAIndicator(macd, 18);
        
        // Entry rule
        Rule entryRule = new OverIndicatorRule(shortEma, longEma) // Trend
                .and(new CrossedDownIndicatorRule(stochasticOscillK, Decimal.valueOf(20))) // Signal 1
                .and(new OverIndicatorRule(macd, emaMacd)); // Signal 2
        
        // Exit rule
        Rule exitRule = new UnderIndicatorRule(shortEma, longEma) // Trend
                .and(new CrossedUpIndicatorRule(stochasticOscillK, Decimal.valueOf(80))) // Signal 1
                .and(new UnderIndicatorRule(macd, emaMacd)); // Signal 2
        
        return new BaseStrategy(entryRule, exitRule);
    }

    /**
     * @param series a time series
     * @return a CCI correction strategy
     */
    public Strategy buildCCICorrectionStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        CCIIndicator longCci = new CCIIndicator(series, 200);
        CCIIndicator shortCci = new CCIIndicator(series, 5);
        Decimal plus100 = Decimal.HUNDRED;
        Decimal minus100 = Decimal.valueOf(-100);
        
        Rule entryRule = new OverIndicatorRule(longCci, plus100) // Bull trend
                .and(new UnderIndicatorRule(shortCci, minus100)); // Signal
        
        Rule exitRule = new UnderIndicatorRule(longCci, minus100) // Bear trend
                .and(new OverIndicatorRule(shortCci, plus100)); // Signal
        
        Strategy strategy = new BaseStrategy(entryRule, exitRule);
        strategy.setUnstablePeriod(5);
        return strategy;
    }
    
    /**
     * @param series a time series
     * @return a global extrema strategy
     */
    public Strategy buildGlobalExtremaStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        int bars_per_day = Math.round(60f * (60f / barSeconds) * 24f);
        
        ClosePriceIndicator closePrices = new ClosePriceIndicator(series);

        // Getting the max price over the past day
        MaxPriceIndicator maxPrices = new MaxPriceIndicator(series);
        HighestValueIndicator dayMaxPrice = new HighestValueIndicator(maxPrices, bars_per_day);
        // Getting the min price over the past day
        MinPriceIndicator minPrices = new MinPriceIndicator(series);
        LowestValueIndicator dayMinPrice = new LowestValueIndicator(minPrices, bars_per_day);

        // Going long if the close price goes below the min price
        MultiplierIndicator downDay = new MultiplierIndicator(dayMinPrice, Decimal.valueOf("1.005"));
        Rule buyingRule = new UnderIndicatorRule(closePrices, downDay);

        // Going short if the close price goes above the max price
        MultiplierIndicator upDay = new MultiplierIndicator(dayMaxPrice, Decimal.valueOf("0.995"));
        Rule sellingRule = new OverIndicatorRule(closePrices, upDay);

        return new BaseStrategy(buyingRule, sellingRule);
    }
    
    /**
     * @param series a time series
     * @return a 2-period RSI strategy
     */
    public static Strategy buildRSI2Strategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator shortSma = new SMAIndicator(closePrice, 5);
        SMAIndicator longSma = new SMAIndicator(closePrice, 200);

        // We use a 2-period RSI indicator to identify buying
        // or selling opportunities within the bigger trend.
        RSIIndicator rsi = new RSIIndicator(closePrice, 2);
        
        // Entry rule
        // The long-term trend is up when a security is above its 200-period SMA.
        Rule entryRule = new OverIndicatorRule(shortSma, longSma) // Trend
                .and(new CrossedDownIndicatorRule(rsi, Decimal.valueOf(5))) // Signal 1
                .and(new OverIndicatorRule(shortSma, closePrice)); // Signal 2
        
        // Exit rule
        // The long-term trend is down when a security is below its 200-period SMA.
        Rule exitRule = new UnderIndicatorRule(shortSma, longSma) // Trend
                .and(new CrossedUpIndicatorRule(rsi, Decimal.valueOf(95))) // Signal 1
                .and(new UnderIndicatorRule(shortSma, closePrice)); // Signal 2
        
        // TODO: Finalize the strategy
        
        return new BaseStrategy(entryRule, exitRule);
    }
    
    
    public void resetStrategies() {
        markers.clear();
        strategies.clear();
        strategies.put("MovingMomentum", buildMovingMomentumStrategy(series));
        strategies.put("CCICorrection", buildCCICorrectionStrategy(series));
        strategies.put("RSI2", buildRSI2Strategy(series));
        strategies.put("GlobalExtrema", buildGlobalExtremaStrategy(series));
        strategies.put("Simple MA", buildSimpleStrategy(series));
        strategies.put("Advanced EMA", buildAdvancedEMAStrategy(series));
        strategies.put("Ichimoku", buildIchimokuStrategy(series));
        strategies.put("Ichimoku2", buildIchimoku2Strategy(series));
        strategies.put("Ichimoku3", buildIchimoku3Strategy(series));
        strategies.put("My WIP Strategy", buildMyMainStrategy(series));
        strategies.put("No strategy", buildNoStrategy(series));
        if (mainStrategy.equals("Auto")) {
            mainStrategy = findOptimalStrategy();
            app.log("Optimal strategy for " + groupName + " is " + mainStrategy, true, false);
        }
        if (!strategies.containsKey(mainStrategy)) {
            List<String> keysAsArray = new ArrayList<String>(strategies.keySet());
            Random r = new Random();
            mainStrategy = keysAsArray.get(r.nextInt(keysAsArray.size()));
            app.log("Strategy for " + groupName + " is not found. Using random = " + mainStrategy, true, false);
        }

        TimeSeriesManager seriesManager = new TimeSeriesManager(series);
        TradingRecord tradingRecord = seriesManager.run(strategies.get(mainStrategy));
        
        List<org.ta4j.core.Trade> trlist = tradingRecord.getTrades();
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

        /**
         * Analysis criteria
         */
        app.log("");
        app.log("Current strategy ("+mainStrategy+") criterias:");
        TotalProfitCriterion totalProfit = new TotalProfitCriterion();
        app.log("Total profit: " + totalProfit.calculate(series, tradingRecord));
        app.log("Average profit (per tick): " + new AverageProfitCriterion().calculate(series, tradingRecord));
        app.log("Number of trades: " + new NumberOfTradesCriterion().calculate(series, tradingRecord));
        app.log("Profitable trades ratio: " + new AverageProfitableTradesCriterion().calculate(series, tradingRecord));
        app.log("Maximum drawdown: " + new MaximumDrawdownCriterion().calculate(series, tradingRecord));
        app.log("Reward-risk ratio: " + new RewardRiskRatioCriterion().calculate(series, tradingRecord));
        app.log("Total transaction cost (from $1000): " + new LinearTransactionCostCriterion(1000, 0.01f * profitsChecker.getTradeComissionPercent()).calculate(series, tradingRecord));
        app.log("Buy-and-hold: " + new BuyAndHoldCriterion().calculate(series, tradingRecord));
        app.log("Custom strategy profit vs buy-and-hold strategy profit: " + new VersusBuyAndHoldCriterion(totalProfit).calculate(series, tradingRecord));
        app.log("");
    }
    
    private String findOptimalStrategy() {
        String result = "Unknown";
        List<Strategy> slist = new ArrayList<Strategy>(strategies.values());
        slist.add(0, this.buildNoStrategy(series));
        List<TimeSeries> subseries = splitSeries(series, Duration.ofSeconds(barSeconds * 30), Duration.ofSeconds(barSeconds * 720));
        AnalysisCriterion profitCriterion = new TotalProfitCriterion();
        Map<String, Integer> successMap = new HashMap<String, Integer>();
        
        String log = "";
        
        for (TimeSeries slice : subseries) {
            // For each sub-series...
            log += "Sub-series: " + slice.getSeriesPeriodDescription() + "\n";
            TimeSeriesManager sliceManager = new TimeSeriesManager(slice);
            for (Map.Entry<String, Strategy> entry : strategies.entrySet()) {
                Strategy strategy = entry.getValue();
                TradingRecord tradingRecord = sliceManager.run(strategy);
                double profit = profitCriterion.calculate(slice, tradingRecord);
                log += "\tProfit for " + entry.getKey() + ": " + profit + "\n";
            }
            Strategy bestStrategy = profitCriterion.chooseBest(sliceManager, slist);
            
            String bestKey = result;
            for (Map.Entry<String, Strategy> entry : strategies.entrySet()) {
                if (Objects.equals(bestStrategy, entry.getValue())) {
                    bestKey = entry.getKey();
                }
            }
            if (!successMap.containsKey(bestKey)) {
                successMap.put(bestKey, 0);
            }
            successMap.put(bestKey, successMap.get(bestKey)+1);
            log += "\t\t--> Best strategy: " + bestKey + "\n";
        }
        
        int max_value = 0;
        if (successMap.containsKey(result)) {
            successMap.remove(result);
        }
        for (Map.Entry<String, Integer> entry : successMap.entrySet()) {
            if (entry.getValue() > max_value) {
                max_value = entry.getValue();
                result = entry.getKey();
            }
        }
        
        app.log(log);
        
        return result;
    }
    
    /**
     * Builds a list of split indexes from splitDuration.
     * @param series the time series to get split begin indexes of
     * @param splitDuration the duration between 2 splits
     * @return a list of begin indexes after split
     */
    public static List<Integer> getSplitBeginIndexes(TimeSeries series, Duration splitDuration) {
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
    public static TimeSeries subseries(TimeSeries series, int beginIndex, Duration duration) {

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
    public static List<TimeSeries> splitSeries(TimeSeries series, Duration splitDuration, Duration sliceDuration) {
        ArrayList<TimeSeries> subseries = new ArrayList<>();
        if (splitDuration != null && !splitDuration.isZero() && 
            sliceDuration != null && !sliceDuration.isZero()) {

            List<Integer> beginIndexes = getSplitBeginIndexes(series, splitDuration);
            for (Integer subseriesBegin : beginIndexes) {
                subseries.add(subseries(series, subseriesBegin, sliceDuration));
            }
        }
        return subseries;
    }
    
    public StrategiesAction checkStatus(boolean is_hodling, boolean checkOtherStrategies, StrategiesMode mode) {
        
        int endIndex = series.getEndIndex();
        float curPrice = series.getBar(endIndex).getClosePrice().floatValue();
        
        if (is_hodling) {
            if (mode != StrategiesMode.BUY_DIP) {
                if (strategies.get(mainStrategy).shouldExit(endIndex)) {
                    addStrategyMarker(false, mainStrategy);
                    return StrategiesAction.DO_LEAVE;
                } else if (strategies.get(mainStrategy).shouldEnter(endIndex)) {
                    addStrategyMarker(true, mainStrategy);
                    app.log(groupName + " should enter here but already hodling...", false, true);
                }
            }
        } else {
            if (strategies.get(mainStrategy).shouldEnter(endIndex)) {
                addStrategyMarker(true, mainStrategy);
                return StrategiesAction.DO_ENTER;
            } else if (strategies.get(mainStrategy).shouldExit(endIndex)) {
                addStrategyMarker(false, mainStrategy);
                app.log(groupName + " should exit here but everything is sold...", false, true);
            }
        }

        if (checkOtherStrategies && mode != StrategiesMode.BUY_DIP) {
            for (Map.Entry<String, Strategy> entry : strategies.entrySet()) {
                if (entry.getKey() != mainStrategy) {
                    if (is_hodling) {
                        if (entry.getValue().shouldExit(endIndex)) {
                            addStrategyMarker(false, entry.getKey());
                            app.log(groupName + " should exit here ("+entry.getKey()+")...", false, true);
                            if (mode != StrategiesMode.SELL_UP) {
                                return StrategiesAction.DO_LEAVE;
                            }
                        } else if (entry.getValue().shouldEnter(endIndex)) {
                            addStrategyMarker(true, entry.getKey());
                            app.log(groupName + " should enter here ("+entry.getKey()+") but already hodling...", false, true);
                        }
                    } else {
                        if (entry.getValue().shouldEnter(endIndex)) {
                            addStrategyMarker(true, entry.getKey());
                            app.log(groupName + " should enter here ("+entry.getKey()+")...", false, true);
                        } else if (entry.getValue().shouldExit(endIndex)) {
                            addStrategyMarker(false, entry.getKey());
                            app.log(groupName + " should exit here ("+entry.getKey()+") but everything is sold...", false, true);
                        }
                    }
                }
            }
        }
        
        profitsChecker.setPairPrice(groupName, curPrice);
        return StrategiesAction.DO_NOTHING;
    }
}
