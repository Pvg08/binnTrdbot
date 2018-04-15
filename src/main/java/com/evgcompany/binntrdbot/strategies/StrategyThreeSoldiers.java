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
        config.Add("TWS-TimeFrame", new StrategyConfigItem("1", "10", "1", "2"));
        config.Add("TWS-Factor", new StrategyConfigItem("0.5", "10", "0.5", "5"));
        config.Add("TBC-TimeFrame", new StrategyConfigItem("1", "10", "1", "1"));
        config.Add("TBC-Factor", new StrategyConfigItem("0.5", "10", "0.5", "1"));
    }

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        initializer = (tseries, trecord, dataset) -> {};
        ThreeWhiteSoldiersIndicator tws = new ThreeWhiteSoldiersIndicator(
            series,
            config.GetIntValue("TWS-TimeFrame"),
            config.GetNumValue("TWS-Factor")
        );
        ThreeBlackCrowsIndicator tbc = new ThreeBlackCrowsIndicator(
            series,
            config.GetIntValue("TBC-TimeFrame"),
            config.GetNumValue("TBC-Factor")
        );
        return new BaseStrategy(
            new BooleanIndicatorRule(tws),
            addStopLossGain(new BooleanIndicatorRule(tbc), series)
        );
    }
}
