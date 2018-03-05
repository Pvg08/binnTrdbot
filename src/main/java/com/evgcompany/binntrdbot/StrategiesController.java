/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import com.binance.api.client.SyncedTime;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import org.ta4j.core.*;
import org.ta4j.core.analysis.criteria.*;
import org.ta4j.core.indicators.*;
import org.ta4j.core.indicators.bollinger.*;
import org.ta4j.core.indicators.candles.*;
import org.ta4j.core.indicators.helpers.*;
import org.ta4j.core.indicators.ichimoku.*;
import org.ta4j.core.indicators.keltner.*;
import org.ta4j.core.indicators.statistics.*;
import org.ta4j.core.trading.rules.*;

/**
 *
 * @author EVG_adm_T
 */
public class StrategiesController {
    
    public class StrategyMarker {
        String label = "";
        double timeStamp = 0;
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
    HashMap<String, Strategy> strategies = new HashMap<String, Strategy>();
    private String mainStrategy = "Auto";
    private int barSeconds;
    private String groupName;
    private List<StrategyMarker> markers = new ArrayList<>();
    private TradingRecord tradingRecord = null;
    
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
                addStopLossGain(new BooleanRule(false), series)
        );
    }
    
    /**
     * @param series a time series
     * @return a dummy strategy
     */
    private Strategy buildSMAStrategy(TimeSeries series) {
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
                addStopLossGain(new UnderIndicatorRule(sma, closePrice), series)
        );
    }

    /**
     * @param series a time series
     * @return a dummy strategy
     */
    private Strategy buildBearishEngulfingEMAStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        EMAIndicator ema_long = new EMAIndicator(closePrice, 21);
        EMAIndicator ema_short = new EMAIndicator(closePrice, 9);

        Rule entryRule = new CrossedUpIndicatorRule(ema_short, ema_long);
        
        // Exit rule
        Rule exitRule = new BooleanIndicatorRule(new BearishEngulfingIndicator(series));
        
        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
    }
    
    /**
     * @param series a time series
     * @return a strategy
     */
    private Strategy buildThreeSoldiersStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        ThreeWhiteSoldiersIndicator tws = new ThreeWhiteSoldiersIndicator(series, 2, Decimal.valueOf(5));
        ThreeBlackCrowsIndicator tbc = new ThreeBlackCrowsIndicator(series, 1, Decimal.valueOf(1));

        return new BaseStrategy(
            new BooleanIndicatorRule(tws),
            addStopLossGain(new BooleanIndicatorRule(tbc), series)
        );
    }
    
    /**
     * @param series a time series
     * @return a strategy
     */
    private Strategy buildKAMAStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        KAMAIndicator kama2 = new KAMAIndicator(closePrice, 10, 2, 30);
        KAMAIndicator kama5 = new KAMAIndicator(closePrice, 10, 5, 30);
        
        // Entry rule
        Rule entryRule = new UnderIndicatorRule(kama5, kama2)
                .and(new CrossedUpIndicatorRule(kama2, closePrice));
        
        // Exit rule
        Rule exitRule = new OverIndicatorRule(kama5, kama2)
                .and(new CrossedDownIndicatorRule(kama2, closePrice));
        
        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
    }
    
    
    /**
     * @param series a time series
     * @return a strategy
     */
    private Strategy buildBollingerStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        MinPriceIndicator minPrice = new MinPriceIndicator(series);
        MaxPriceIndicator maxPrice = new MaxPriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(closePrice, 2);
        
        EMAIndicator avg14 = new EMAIndicator(closePrice, 14);
        StandardDeviationIndicator sd14 = new StandardDeviationIndicator(closePrice, 14);
        
        BollingerBandsMiddleIndicator bollingerM = new BollingerBandsMiddleIndicator(avg14);
        BollingerBandsLowerIndicator bollingerL = new BollingerBandsLowerIndicator(bollingerM, sd14);
        BollingerBandsUpperIndicator bollingerU = new BollingerBandsUpperIndicator(bollingerM, sd14);
        
        // Entry rule
        Rule entryRule = new CrossedDownIndicatorRule(bollingerL, minPrice)
                .and(new InPipeRule(rsi, Decimal.valueOf(55), Decimal.valueOf(0)))
                .and(new IsRisingRule(rsi, 1));
                
                /*new UnderIndicatorRule(bollingerL, closePrice)
                .and(new OverIndicatorRule(bollingerM, closePrice))
            .and(new InPipeRule(rsi, Decimal.valueOf(55), Decimal.valueOf(25)))
            .and(new IsRisingRule(rsi, 2))
            .and(
                    new InPipeRule(new PreviousValueIndicator(bollingerU, 1), new PreviousValueIndicator(maxPrice, 1), new PreviousValueIndicator(minPrice, 1))
                .or(new InPipeRule(new PreviousValueIndicator(bollingerU, 2), new PreviousValueIndicator(maxPrice, 2), new PreviousValueIndicator(minPrice, 2)))
                .or(new InPipeRule(new PreviousValueIndicator(bollingerU, 3), new PreviousValueIndicator(maxPrice, 3), new PreviousValueIndicator(minPrice, 3)))
                .or(new InPipeRule(new PreviousValueIndicator(bollingerU, 4), new PreviousValueIndicator(maxPrice, 4), new PreviousValueIndicator(minPrice, 4)))
                .or(new InPipeRule(new PreviousValueIndicator(bollingerU, 5), new PreviousValueIndicator(maxPrice, 5), new PreviousValueIndicator(minPrice, 5)))
                .or(new InPipeRule(new PreviousValueIndicator(bollingerU, 6), new PreviousValueIndicator(maxPrice, 6), new PreviousValueIndicator(minPrice, 6)))
                .or(new InPipeRule(new PreviousValueIndicator(bollingerU, 7), new PreviousValueIndicator(maxPrice, 7), new PreviousValueIndicator(minPrice, 7)))
                .or(new InPipeRule(new PreviousValueIndicator(bollingerU, 8), new PreviousValueIndicator(maxPrice, 8), new PreviousValueIndicator(minPrice, 8)))
                .or(new InPipeRule(new PreviousValueIndicator(bollingerU, 9), new PreviousValueIndicator(maxPrice, 9), new PreviousValueIndicator(minPrice, 9)))
            )*/;
        
        // Exit rule
        Rule exitRule = new CrossedUpIndicatorRule(bollingerU, maxPrice) 
                .and(new InPipeRule(rsi, Decimal.valueOf(100), Decimal.valueOf(45)))
                .and(new IsFallingRule(rsi, 1));
        
        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
    }
    
    /**
     * @param series a time series
     * @return a strategy
     */
    private Strategy buildBollinger2Strategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        
        EMAIndicator avg14 = new EMAIndicator(closePrice, 14);
        StandardDeviationIndicator sd14 = new StandardDeviationIndicator(closePrice, 14);
        
        BollingerBandsMiddleIndicator bollingerM = new BollingerBandsMiddleIndicator(avg14);
        BollingerBandsLowerIndicator BBB = new BollingerBandsLowerIndicator(bollingerM, sd14, Decimal.TWO);
        BollingerBandsUpperIndicator BBT = new BollingerBandsUpperIndicator(bollingerM, sd14, Decimal.TWO);
        
        Rule entryRule = new OverIndicatorRule(closePrice, BBB)
                .and(new UnderIndicatorRule(new PreviousValueIndicator(closePrice, 1), new PreviousValueIndicator(BBB, 1)));
        
        Rule exitRule = new UnderIndicatorRule(closePrice, BBT)
                .and(new OverIndicatorRule(new PreviousValueIndicator(closePrice, 1), new PreviousValueIndicator(BBT, 1)));
        
        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
    }
    
    /**
     * @param series a time series
     * @return a strategy
     */
    private Strategy buildCMOStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        
        CMOIndicator cmo = new CMOIndicator(closePrice, 50);
        
        Rule entryRule = new UnderIndicatorRule(cmo, Decimal.valueOf(-10));
        
        Rule exitRule = new OverIndicatorRule(cmo, Decimal.valueOf(10));
        
        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
    }
    
    /**
     * @param series a time series
     * @return a strategy
     */
    private Strategy buildKeltnerChannelStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        MACDIndicator macd = new MACDIndicator(closePrice, 9, 15);
        
        KeltnerChannelMiddleIndicator keltnerM = new KeltnerChannelMiddleIndicator(closePrice, 20);
        KeltnerChannelLowerIndicator keltnerL = new KeltnerChannelLowerIndicator(keltnerM, Decimal.valueOf(1.5), 20);
        KeltnerChannelUpperIndicator keltnerU = new KeltnerChannelUpperIndicator(keltnerM, Decimal.valueOf(1.5), 20);
        
        // Entry rule
        Rule entryRule = new UnderIndicatorRule(keltnerL, closePrice)
                .and(new UnderIndicatorRule(new PreviousValueIndicator(keltnerL, 1), new PreviousValueIndicator(closePrice, 1)))
                .and(new OverIndicatorRule(keltnerM, closePrice))
                .and(new IsRisingRule(macd, 2));
        
        // Exit rule
        Rule exitRule = new OverIndicatorRule(keltnerU, closePrice)
                .and(new OverIndicatorRule(new PreviousValueIndicator(keltnerU, 1), new PreviousValueIndicator(closePrice, 1)))
                .and(new UnderIndicatorRule(keltnerM, closePrice))
                .and(new IsFallingRule(macd, 2));
        
        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
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
        
        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
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
        
        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
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
        
        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
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
        
        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
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
        
        IchimokuTenkanSenIndicator tenkanSen = new IchimokuTenkanSenIndicator(series, 3);
        IchimokuKijunSenIndicator kijunSen = new IchimokuKijunSenIndicator(series, 5);
        IchimokuSenkouSpanAIndicator senkouSpanA = new IchimokuSenkouSpanAIndicator(series, tenkanSen, kijunSen);
        IchimokuSenkouSpanBIndicator senkouSpanB = new IchimokuSenkouSpanBIndicator(series, 9);
        IchimokuChikouSpanIndicator chikouSpan = new IchimokuChikouSpanIndicator(series, 5);
        
        EMAIndicator shortEma = new EMAIndicator(closePrice, 5);
        EMAIndicator longEma = new EMAIndicator(closePrice, 20);

        RSIIndicator rsi = new RSIIndicator(closePrice, 2);
        SMAIndicator sma = new SMAIndicator(closePrice, 12);
        MACDIndicator macd = new MACDIndicator(closePrice, 9, 15);

        Rule entryRule = new CrossedDownIndicatorRule(closePrice, kijunSen)
                //new OverIndicatorRule(shortEma, longEma)
                //.and(new OverIndicatorRule(shortEma, closePrice))
                //.and(new OverIndicatorRule(sma, closePrice))
                //.and(new IsRisingRule(macd, 2))
                .and(new UnderIndicatorRule(rsi, new ConstantIndicator(Decimal.valueOf(90))))
                ;

        Rule exitRule = new CrossedUpIndicatorRule(closePrice, kijunSen)
                //new UnderIndicatorRule(shortEma, longEma)
                //.and(new UnderIndicatorRule(shortEma, closePrice))
                //.and(new UnderIndicatorRule(sma, closePrice))
                //.and(new IsFallingRule(macd, 2))
                .and(new OverIndicatorRule(rsi, new ConstantIndicator(Decimal.valueOf(10))))
                ;

        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
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
        
        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
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
        
        Strategy strategy = new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
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

        return new BaseStrategy(buyingRule, addStopLossGain(sellingRule, series));
    }
    
    /**
     * @param series a time series
     * @return a 2-period RSI strategy
     */
    public Strategy buildRSI2Strategy(TimeSeries series) {
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
        
        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
    }
    
    private Rule addStopLossGain(Rule rule, TimeSeries series) {
        if (profitsChecker != null) {
            if (profitsChecker.isLowHold()) {
                rule = rule.and(new StopGainRule(new ClosePriceIndicator(series), Decimal.valueOf(profitsChecker.getTradeComissionPercent().multiply(BigDecimal.valueOf(2)))));
            }
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
        TimeSeriesManager seriesManager = new TimeSeriesManager(series);
        TradingRecord tradingRecord = seriesManager.run(strategies.get(mainStrategy));

        if (init_marks) {
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
        }
        
        /**
         * Analysis criteria
         */
        app.log("");
        app.log("Current "+groupName+" strategy ("+mainStrategy+") criterias:");
        TotalProfitCriterion totalProfit = new TotalProfitCriterion();
        app.log("Total profit: " + totalProfit.calculate(series, tradingRecord));
        app.log("Total transaction cost: " + new LinearTransactionCostCriterion(1, 0.01f * profitsChecker.getTradeComissionPercent().doubleValue()).calculate(series, tradingRecord));
        app.log("Average profit (per tick): " + new AverageProfitCriterion().calculate(series, tradingRecord));
        app.log("Maximum drawdown: " + new MaximumDrawdownCriterion().calculate(series, tradingRecord));
        app.log("Reward-risk ratio: " + new RewardRiskRatioCriterion().calculate(series, tradingRecord));
        app.log("Buy-and-hold: " + new BuyAndHoldCriterion().calculate(series, tradingRecord));
        app.log("");
        app.log("Number of trades: " + new NumberOfTradesCriterion().calculate(series, tradingRecord));
        app.log("Profitable trades ratio: " + new AverageProfitableTradesCriterion().calculate(series, tradingRecord));
        app.log("Profit without comission: " + new ProfitWithoutComissionCriterion(0.01f * profitsChecker.getTradeComissionPercent().doubleValue()).calculate(series, tradingRecord));
        app.log("Custom strategy profit vs buy-and-hold strategy profit: " + new VersusBuyAndHoldCriterion(totalProfit).calculate(series, tradingRecord));
        app.log("");app.log("");
    }
    
    public void resetStrategies() {
        markers.clear();
        strategies.clear();
        HashMap<String, StrategyInitializer> strategies_init = getStrategiesInitializerMap();
        for (Map.Entry<String, StrategyInitializer> entry : strategies_init.entrySet()) {
            strategies.put(entry.getKey(), entry.getValue().onInit(series));
        }
        strategies_init.clear();
        strategies_init = null;
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

        logStatistics(true);
    }
    
    private String findOptimalStrategy() {
        String result = "Unknown";
        List<Strategy> slist = new ArrayList<Strategy>(strategies.values());
        List<TimeSeries> subseries = splitSeries(series, Duration.ofSeconds(barSeconds * 30), Duration.ofSeconds(barSeconds * 1440));
        AnalysisCriterion profitwotranscostCriterion = new ProfitWithoutComissionCriterion(0.01f * profitsChecker.getTradeComissionPercent().doubleValue());
        Map<String, Integer> successMap = new HashMap<String, Integer>();
        
        String log = "";
        
        for (TimeSeries slice : subseries) {
            // For each sub-series...
            log += "Sub-series: " + slice.getSeriesPeriodDescription() + "\n";
            TimeSeriesManager sliceManager = new TimeSeriesManager(slice);
            for (Map.Entry<String, Strategy> entry : strategies.entrySet()) {
                Strategy strategy = entry.getValue();
                TradingRecord tradingRecord = sliceManager.run(strategy);
                double profit = profitwotranscostCriterion.calculate(slice, tradingRecord);
                log += "\tProfit wo comission for " + entry.getKey() + ": " + profit + "\n";
            }
            Strategy bestStrategy = profitwotranscostCriterion.chooseBest(sliceManager, slist);
            
            String bestKey = result;
            for (Map.Entry<String, Strategy> entry : strategies.entrySet()) {
                if (Objects.equals(bestStrategy, entry.getValue())) {
                    bestKey = entry.getKey();
                }
            }
            if (!successMap.containsKey(bestKey)) {
                successMap.put(bestKey, 1);
            }
            successMap.put(bestKey, successMap.get(bestKey)+1);
            log += "\t\t--> Best strategy: " + bestKey + "\n";
        }
        
        int max_value = 0;
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
        
        if (is_hodling) {
            if (mode != StrategiesMode.BUY_DIP) {
                if (strategies.get(mainStrategy).shouldExit(endIndex, tradingRecord)) {
                    addStrategyMarker(false, mainStrategy);
                    return StrategiesAction.DO_LEAVE;
                } else if (strategies.get(mainStrategy).shouldEnter(endIndex, tradingRecord)) {
                    addStrategyMarker(true, mainStrategy);
                    app.log(groupName + " should enter here but already hodling...", false, true);
                }
            }
        } else {
            if (strategies.get(mainStrategy).shouldEnter(endIndex, tradingRecord)) {
                addStrategyMarker(true, mainStrategy);
                return StrategiesAction.DO_ENTER;
            } else if (strategies.get(mainStrategy).shouldExit(endIndex, tradingRecord)) {
                addStrategyMarker(false, mainStrategy);
                app.log(groupName + " should exit here but everything is sold...", false, true);
            }
        }

        if (checkOtherStrategies && mode != StrategiesMode.BUY_DIP) {
            for (Map.Entry<String, Strategy> entry : strategies.entrySet()) {
                if (entry.getKey() != mainStrategy) {
                    if (is_hodling) {
                        if (entry.getValue().shouldExit(endIndex, tradingRecord)) {
                            addStrategyMarker(false, entry.getKey());
                            app.log(groupName + " should exit here ("+entry.getKey()+")...", false, true);
                            if (mode != StrategiesMode.SELL_UP) {
                                return StrategiesAction.DO_LEAVE;
                            }
                        } else if (entry.getValue().shouldEnter(endIndex, tradingRecord)) {
                            addStrategyMarker(true, entry.getKey());
                            app.log(groupName + " should enter here ("+entry.getKey()+") but already hodling...", false, true);
                        }
                    } else {
                        if (entry.getValue().shouldEnter(endIndex, tradingRecord)) {
                            addStrategyMarker(true, entry.getKey());
                            app.log(groupName + " should enter here ("+entry.getKey()+")...", false, true);
                        } else if (entry.getValue().shouldExit(endIndex, tradingRecord)) {
                            addStrategyMarker(false, entry.getKey());
                            app.log(groupName + " should exit here ("+entry.getKey()+") but everything is sold...", false, true);
                        }
                    }
                }
            }
        }
        return StrategiesAction.DO_NOTHING;
    }

    public HashMap<String, StrategyInitializer> getStrategiesInitializerMap() {
        HashMap<String, StrategyInitializer> strategies = new HashMap<String, StrategyInitializer>();
        strategies.put("No strategy", s -> buildNoStrategy(s));
        strategies.put("Three Soldiers", s -> buildThreeSoldiersStrategy(s));
        strategies.put("MovingMomentum", s -> buildMovingMomentumStrategy(s));
        strategies.put("CCICorrection", s -> buildCCICorrectionStrategy(s));
        strategies.put("RSI2", s -> buildRSI2Strategy(s));
        strategies.put("GlobalExtrema", s -> buildGlobalExtremaStrategy(s));
        strategies.put("SMA", s -> buildSMAStrategy(s));
        strategies.put("Advanced EMA", s -> buildAdvancedEMAStrategy(s));
        strategies.put("Ichimoku", s -> buildIchimokuStrategy(s));
        strategies.put("Ichimoku2", s -> buildIchimoku2Strategy(s));
        strategies.put("Ichimoku3", s -> buildIchimoku3Strategy(s));
        strategies.put("KAMA", s -> buildKAMAStrategy(s));
        strategies.put("CMO", s -> buildCMOStrategy(s));
        strategies.put("Bollinger", s -> buildBollingerStrategy(s));
        strategies.put("Bollinger2", s -> buildBollinger2Strategy(s));
        strategies.put("Keltner Channel", s -> buildKeltnerChannelStrategy(s));
        strategies.put("Bearish Engulfing EMA", s -> buildBearishEngulfingEMAStrategy(s));
        strategies.put("My WIP Strategy", s -> buildMyMainStrategy(s));
        return strategies;
    }
    
    public List<String> getStrategiesNames() {
        return new ArrayList<String>(strategies.keySet());
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
}
