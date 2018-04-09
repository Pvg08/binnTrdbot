/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.strategies;

import com.evgcompany.binntrdbot.StrategiesController;
import com.evgcompany.binntrdbot.analysis.StrategyConfigItem;
import org.ta4j.core.BaseStrategy;
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
        config.Add("EMA1-TimeFrame", new StrategyConfigItem("2", "40", "1", "9"));
        config.Add("EMA2-TimeFrame", new StrategyConfigItem("9", "51", "3", "21"));
    }

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        
        int ema1_tf = config.GetIntValue("EMA1-TimeFrame");
        int ema2_tf = config.GetIntValue("EMA2-TimeFrame");
        
        initializer = (tseries, dataset) -> {
            ClosePriceIndicator closePrice = new ClosePriceIndicator(tseries);
            EMAIndicator ema_short = new EMAIndicator(closePrice, ema1_tf);
            EMAIndicator ema_long = new EMAIndicator(closePrice, ema2_tf);
            dataset.addSeries(buildChartTimeSeries(tseries, ema_short, "EMA " + ema1_tf));
            dataset.addSeries(buildChartTimeSeries(tseries, ema_long, "EMA " + ema2_tf));
        };
        
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        EMAIndicator ema_short = new EMAIndicator(closePrice, ema1_tf);
        EMAIndicator ema_long = new EMAIndicator(closePrice, ema2_tf);

        Rule entryRule = new CrossedUpIndicatorRule(ema_short, ema_long);
        Rule exitRule = new BooleanIndicatorRule(new BearishEngulfingIndicator(series));

        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
    }
}
