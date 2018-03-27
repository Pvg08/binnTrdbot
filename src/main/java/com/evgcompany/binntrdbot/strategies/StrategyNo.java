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
import org.ta4j.core.trading.rules.BooleanRule;

/**
 *
 * @author EVG_Adminer
 */
public class StrategyNo extends StrategyItem {

    public StrategyNo(StrategiesController controller) {
        super(controller);
        StrategyName = "No Strategy";
    }

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        initializer = (tseries, dataset) -> {};
        return new BaseStrategy(
            new BooleanRule(false),
            new BooleanRule(false)
        );
    }
    
}
