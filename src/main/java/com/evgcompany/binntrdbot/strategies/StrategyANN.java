/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.strategies;

import com.evgcompany.binntrdbot.StrategiesController;
import com.evgcompany.binntrdbot.analysis.ANNIndicator;
import com.evgcompany.binntrdbot.analysis.OHLC4Indicator;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Decimal;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;

/**
 *
 * @author EVG_Adminer
 */
public class StrategyANN extends StrategyItem {

    public StrategyANN(StrategiesController controller) {
        super(controller);
        StrategyName = "ANN";
    }

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        initializer = (tseries, dataset) -> {};
        
        double threshold = 0.0014;
        
        OHLC4Indicator ohlc = new OHLC4Indicator(series);
        ANNIndicator ann_enter = new ANNIndicator(ohlc, 96);
        ANNIndicator ann_exit = new ANNIndicator(ohlc, 1);

        Rule entryRule = new UnderIndicatorRule(ann_enter, Decimal.valueOf(-threshold))
                .and(new UnderIndicatorRule(ann_exit, Decimal.valueOf(-threshold)));

        Rule exitRule = new OverIndicatorRule(ann_enter, Decimal.valueOf(threshold))
                .and(new OverIndicatorRule(ann_exit, Decimal.valueOf(threshold)));

        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
    }
}
