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
import org.ta4j.core.indicators.ParabolicSarIndicator;
import org.ta4j.core.indicators.helpers.MaxPriceIndicator;
import org.ta4j.core.indicators.helpers.MinPriceIndicator;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;


/**
 *
 * @author EVG_Adminer
 */
public class StrategyParabolicSAR extends StrategyItem {

    public StrategyParabolicSAR(StrategiesController controller) {
        super(controller);
        StrategyName = "Parabolic SAR";
    }

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        
        double aF = 0.02;
        double maxA = 0.2;
        double incr = 0.02;
        
        initializer = (tseries, dataset) -> {
            ParabolicSarIndicator psar = new ParabolicSarIndicator(series, Decimal.valueOf(aF), Decimal.valueOf(maxA), Decimal.valueOf(incr));
            dataset.addSeries(buildChartTimeSeries(tseries, psar, "Parabolic SAR"));
        };
        
        MaxPriceIndicator maxPrice = new MaxPriceIndicator(series);
        MinPriceIndicator minPrice = new MinPriceIndicator(series);
        ParabolicSarIndicator psar = new ParabolicSarIndicator(series, Decimal.valueOf(aF), Decimal.valueOf(maxA), Decimal.valueOf(incr));
        
        Rule entryRule = new OverIndicatorRule(psar, maxPrice);
        
        Rule exitRule = new UnderIndicatorRule(psar, minPrice);
        
        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
    }
}
