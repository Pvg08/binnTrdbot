/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.strategies;

import com.evgcompany.binntrdbot.strategies.core.*;
import com.evgcompany.binntrdbot.analysis.*;
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
        config.Add("CMO-TimeFrame", new StrategyConfigItem("10", "100", "5", "50"));
        config.Add("CMO-Threshold", new StrategyConfigItem("5", "45", "5", "10"));
    }

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        
        int timeframe = config.GetIntValue("CMO-TimeFrame");
        int threshold = config.GetIntValue("CMO-Threshold");
        
        initializer = (tseries, dataset) -> {};

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        CMOIndicator cmo = new CMOIndicator(closePrice, timeframe);

        Rule entryRule = new UnderIndicatorRule(cmo, Decimal.valueOf(-threshold));
        Rule exitRule = new OverIndicatorRule(cmo, Decimal.valueOf(threshold));
        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
    }
}
