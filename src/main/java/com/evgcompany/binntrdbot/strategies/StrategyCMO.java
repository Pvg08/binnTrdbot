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
import org.ta4j.core.indicators.CMOIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;

/**
 *
 * @author EVG_Adminer
 */
public class StrategyCMO extends StrategyItem {

    public StrategyCMO(StrategiesController controller) {
        super(controller);
        StrategyName = "CMO";
    }

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        initializer = (tseries, dataset) -> {};

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        CMOIndicator cmo = new CMOIndicator(closePrice, 50);

        Rule entryRule = new UnderIndicatorRule(cmo, Decimal.valueOf(-10));
        Rule exitRule = new OverIndicatorRule(cmo, Decimal.valueOf(10));
        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
    }
    
}
