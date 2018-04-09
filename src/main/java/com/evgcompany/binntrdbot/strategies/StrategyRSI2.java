/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.strategies;

import com.evgcompany.binntrdbot.StrategiesController;
import com.evgcompany.binntrdbot.analysis.StrategyConfigItem;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Decimal;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.trading.rules.CrossedDownIndicatorRule;
import org.ta4j.core.trading.rules.CrossedUpIndicatorRule;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;

/**
 *
 * @author EVG_Adminer
 */
public class StrategyRSI2 extends StrategyItem {

    public StrategyRSI2(StrategiesController controller) {
        super(controller);
        StrategyName = "RSI2";
        config.Add("SMA1-TimeFrame", new StrategyConfigItem("3", "31", "2", "5"));
        config.Add("SMA2-TimeFrame", new StrategyConfigItem("21", "151", "10", "151"));
        config.Add("RSI-TimeFrame", new StrategyConfigItem("2", "15", "1", "2"));
        config.Add("RSI-Threshold", new StrategyConfigItem("1", "25", "1", "5"));
    }

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        
        int sma1_timeFrame = config.GetIntValue("SMA1-TimeFrame");
        int sma2_timeFrame = config.GetIntValue("SMA2-TimeFrame");
        int rsi_timeFrame = config.GetIntValue("RSI-TimeFrame");
        int rsi_threshold = config.GetIntValue("RSI-Threshold");
        
        initializer = (tseries, dataset) -> {
            ClosePriceIndicator closePrice = new ClosePriceIndicator(tseries);
            SMAIndicator shortSma = new SMAIndicator(closePrice, sma1_timeFrame);
            SMAIndicator longSma = new SMAIndicator(closePrice, sma2_timeFrame);
            dataset.addSeries(buildChartTimeSeries(tseries, shortSma, "SMA " + sma1_timeFrame));
            dataset.addSeries(buildChartTimeSeries(tseries, longSma, "SMA " + sma2_timeFrame));
        };
        
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator shortSma = new SMAIndicator(closePrice, sma1_timeFrame);
        SMAIndicator longSma = new SMAIndicator(closePrice, sma2_timeFrame);

        RSIIndicator rsi = new RSIIndicator(closePrice, rsi_timeFrame);
        
        Rule entryRule = new OverIndicatorRule(shortSma, longSma)
                .and(new CrossedDownIndicatorRule(rsi, Decimal.valueOf(rsi_threshold)))
                .and(new OverIndicatorRule(shortSma, closePrice));
        
        Rule exitRule = new UnderIndicatorRule(shortSma, longSma)
                .and(new CrossedUpIndicatorRule(rsi, Decimal.valueOf(100-rsi_threshold)))
                .and(new UnderIndicatorRule(shortSma, closePrice));
        
        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
    }
}
