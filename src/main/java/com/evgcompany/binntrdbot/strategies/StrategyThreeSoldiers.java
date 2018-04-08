/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.strategies;

import com.evgcompany.binntrdbot.StrategiesController;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Decimal;
import org.ta4j.core.Strategy;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.candles.ThreeBlackCrowsIndicator;
import org.ta4j.core.indicators.candles.ThreeWhiteSoldiersIndicator;
import org.ta4j.core.trading.rules.BooleanIndicatorRule;

/**
 *
 * @author EVG_Adminer
 */
public class StrategyThreeSoldiers extends StrategyItem {

    public StrategyThreeSoldiers(StrategiesController controller) {
        super(controller);
        StrategyName = "Three Soldiers";
    }

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        initializer = (tseries, dataset) -> {};
        ThreeWhiteSoldiersIndicator tws = new ThreeWhiteSoldiersIndicator(series, 2, Decimal.valueOf(5));
        ThreeBlackCrowsIndicator tbc = new ThreeBlackCrowsIndicator(series, 1, Decimal.valueOf(1));
        return new BaseStrategy(
            new BooleanIndicatorRule(tws),
            addStopLossGain(new BooleanIndicatorRule(tbc), series)
        );
    }
    
}