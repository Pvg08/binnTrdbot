/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.strategies;

import com.evgcompany.binntrdbot.StrategiesController;
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
    }

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        initializer = (tseries, dataset) -> {
            ClosePriceIndicator closePrice = new ClosePriceIndicator(tseries);
            SMAIndicator shortSma = new SMAIndicator(closePrice, 5);
            SMAIndicator longSma = new SMAIndicator(closePrice, 200);
            dataset.addSeries(buildChartTimeSeries(tseries, shortSma, "SMA 5"));
            dataset.addSeries(buildChartTimeSeries(tseries, longSma, "SMA 200"));
        };
        
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator shortSma = new SMAIndicator(closePrice, 5);
        SMAIndicator longSma = new SMAIndicator(closePrice, 200);

        RSIIndicator rsi = new RSIIndicator(closePrice, 2);
        
        Rule entryRule = new OverIndicatorRule(shortSma, longSma)
                .and(new CrossedDownIndicatorRule(rsi, Decimal.valueOf(5)))
                .and(new OverIndicatorRule(shortSma, closePrice));
        
        Rule exitRule = new UnderIndicatorRule(shortSma, longSma)
                .and(new CrossedUpIndicatorRule(rsi, Decimal.valueOf(95)))
                .and(new UnderIndicatorRule(shortSma, closePrice));
        
        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
    }
}
