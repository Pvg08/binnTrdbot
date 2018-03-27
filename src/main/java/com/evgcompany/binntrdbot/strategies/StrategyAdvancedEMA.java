/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.strategies;

import com.evgcompany.binntrdbot.StrategiesController;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;


/**
 *
 * @author EVG_Adminer
 */
public class StrategyAdvancedEMA extends StrategyItem {

    public StrategyAdvancedEMA(StrategiesController controller) {
        super(controller);
        StrategyName = "Advanced EMA";
    }

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        initializer = (tseries, dataset) -> {
            ClosePriceIndicator closePrice = new ClosePriceIndicator(tseries);
            EMAIndicator ema_y = new EMAIndicator(closePrice, 55);
            EMAIndicator ema_1 = new EMAIndicator(closePrice, 21);
            EMAIndicator ema_2 = new EMAIndicator(closePrice, 13);
            EMAIndicator ema_3 = new EMAIndicator(closePrice, 8);
            dataset.addSeries(buildChartTimeSeries(tseries, ema_3, "EMA 8"));
            dataset.addSeries(buildChartTimeSeries(tseries, ema_2, "EMA 13"));
            dataset.addSeries(buildChartTimeSeries(tseries, ema_1, "EMA 21"));
            dataset.addSeries(buildChartTimeSeries(tseries, ema_y, "EMA 55"));
        };
        
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        EMAIndicator ema_y = new EMAIndicator(closePrice, 55);
        EMAIndicator ema_1 = new EMAIndicator(closePrice, 21);
        EMAIndicator ema_2 = new EMAIndicator(closePrice, 13);
        EMAIndicator ema_3 = new EMAIndicator(closePrice, 8);
        
        Rule entryRule = new UnderIndicatorRule(ema_y, ema_1)
                .and(new UnderIndicatorRule(ema_y, ema_2))
                .and(new UnderIndicatorRule(ema_y, ema_3));
        
        Rule exitRule = new OverIndicatorRule(ema_y, ema_1)
                .and(new OverIndicatorRule(ema_y, ema_2))
                .and(new OverIndicatorRule(ema_y, ema_3));
        
        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
    }
}
