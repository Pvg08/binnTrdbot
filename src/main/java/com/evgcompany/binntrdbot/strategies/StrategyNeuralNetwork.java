/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.strategies;

import com.evgcompany.binntrdbot.strategies.core.*;
import com.evgcompany.binntrdbot.analysis.*;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Strategy;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.trading.rules.BooleanRule;

/**
 *
 * @author EVG_Adminer
 */
public class StrategyNeuralNetwork extends StrategyItem {

    public StrategyNeuralNetwork(StrategiesController controller) {
        super(controller);
        StrategyName = "Neural Network";
    }

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        initializer = (tseries, trecord, dataset) -> {};
        return new BaseStrategy(
            new BooleanRule(false),
            addStopLossGain(new BooleanRule(false), series)
        );
    }
    
}
