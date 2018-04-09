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
        config.Add("EMA-TimeFrameBase", new StrategyConfigItem("2", "30", "1", "8"));
    }

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        
        int ema_tf1 = config.GetIntValue("EMA-TimeFrameBase");
        int ema_tf2 = (int) Math.round(ema_tf1 * 13/8);
        int ema_tf3 = (int) Math.round(ema_tf1 * 21/8);
        int ema_tf4 = (int) Math.round(ema_tf1 * 55/8);
        
        initializer = (tseries, dataset) -> {
            ClosePriceIndicator closePrice = new ClosePriceIndicator(tseries);
            EMAIndicator ema_y = new EMAIndicator(closePrice, ema_tf4);
            EMAIndicator ema_1 = new EMAIndicator(closePrice, ema_tf3);
            EMAIndicator ema_2 = new EMAIndicator(closePrice, ema_tf2);
            EMAIndicator ema_3 = new EMAIndicator(closePrice, ema_tf1);
            dataset.addSeries(buildChartTimeSeries(tseries, ema_3, "EMA " + ema_tf1));
            dataset.addSeries(buildChartTimeSeries(tseries, ema_2, "EMA " + ema_tf2));
            dataset.addSeries(buildChartTimeSeries(tseries, ema_1, "EMA " + ema_tf3));
            dataset.addSeries(buildChartTimeSeries(tseries, ema_y, "EMA " + ema_tf4));
        };
        
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        EMAIndicator ema_y = new EMAIndicator(closePrice, ema_tf4);
        EMAIndicator ema_1 = new EMAIndicator(closePrice, ema_tf3);
        EMAIndicator ema_2 = new EMAIndicator(closePrice, ema_tf2);
        EMAIndicator ema_3 = new EMAIndicator(closePrice, ema_tf1);
        
        Rule entryRule = new UnderIndicatorRule(ema_y, ema_1)
                .and(new UnderIndicatorRule(ema_y, ema_2))
                .and(new UnderIndicatorRule(ema_y, ema_3));
        
        Rule exitRule = new OverIndicatorRule(ema_y, ema_1)
                .and(new OverIndicatorRule(ema_y, ema_2))
                .and(new OverIndicatorRule(ema_y, ema_3));
        
        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
    }
}
