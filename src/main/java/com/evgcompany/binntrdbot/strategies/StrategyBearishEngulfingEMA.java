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
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.candles.BearishEngulfingIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.trading.rules.BooleanIndicatorRule;
import org.ta4j.core.trading.rules.CrossedUpIndicatorRule;


/**
 *
 * @author EVG_Adminer
 */
public class StrategyBearishEngulfingEMA extends StrategyItem {

    public StrategyBearishEngulfingEMA(StrategiesController controller) {
        super(controller);
        StrategyName = "Bearish Engulfing EMA";
    }

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        initializer = (tseries, dataset) -> {
            ClosePriceIndicator closePrice = new ClosePriceIndicator(tseries);
            EMAIndicator ema_short = new EMAIndicator(closePrice, 9);
            EMAIndicator ema_long = new EMAIndicator(closePrice, 21);
            dataset.addSeries(buildChartTimeSeries(tseries, ema_short, "EMA 9"));
            dataset.addSeries(buildChartTimeSeries(tseries, ema_long, "EMA 21"));
        };
        
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        EMAIndicator ema_long = new EMAIndicator(closePrice, 21);
        EMAIndicator ema_short = new EMAIndicator(closePrice, 9);

        Rule entryRule = new CrossedUpIndicatorRule(ema_short, ema_long);
        Rule exitRule = new BooleanIndicatorRule(new BearishEngulfingIndicator(series));

        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
    }
}
