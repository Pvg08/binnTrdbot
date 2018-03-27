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
import org.ta4j.core.indicators.KAMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.trading.rules.CrossedDownIndicatorRule;
import org.ta4j.core.trading.rules.CrossedUpIndicatorRule;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;

/**
 *
 * @author EVG_Adminer
 */
public class StrategyKAMA extends StrategyItem {

    public StrategyKAMA(StrategiesController controller) {
        super(controller);
        StrategyName = "KAMA";
    }

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        initializer = (tseries, dataset) -> {
            ClosePriceIndicator closePrice = new ClosePriceIndicator(tseries);
            KAMAIndicator kama2 = new KAMAIndicator(closePrice, 10, 2, 30);
            KAMAIndicator kama5 = new KAMAIndicator(closePrice, 10, 5, 30);
            dataset.addSeries(buildChartTimeSeries(tseries, kama2, "KAMA 2"));
            dataset.addSeries(buildChartTimeSeries(tseries, kama5, "KAMA 5"));
        };

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        KAMAIndicator kama2 = new KAMAIndicator(closePrice, 10, 2, 30);
        KAMAIndicator kama5 = new KAMAIndicator(closePrice, 10, 5, 30);
        
        Rule entryRule = new UnderIndicatorRule(kama5, kama2)
                .and(new CrossedUpIndicatorRule(kama2, closePrice));
        
        Rule exitRule = new OverIndicatorRule(kama5, kama2)
                .and(new CrossedDownIndicatorRule(kama2, closePrice));
        
        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
    }
    
}
