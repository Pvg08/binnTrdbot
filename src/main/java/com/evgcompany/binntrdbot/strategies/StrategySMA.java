/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.strategies;

import com.evgcompany.binntrdbot.StrategiesController;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Strategy;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;

/**
 *
 * @author EVG_Adminer
 */
public class StrategySMA extends StrategyItem {

    public StrategySMA(StrategiesController controller) {
        super(controller);
        StrategyName = "SMA";
    }

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        initializer = (tseries, dataset) -> {
            ClosePriceIndicator closePrice = new ClosePriceIndicator(tseries);
            SMAIndicator sma = new SMAIndicator(closePrice, 12);
            dataset.addSeries(buildChartTimeSeries(tseries, sma, "SMA 12"));
        };
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator sma = new SMAIndicator(closePrice, 12);
        return new BaseStrategy(
                new OverIndicatorRule(sma, closePrice),
                addStopLossGain(new UnderIndicatorRule(sma, closePrice), series)
        );
        
    }
    
}
