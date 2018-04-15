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
import org.ta4j.core.indicators.CCIIndicator;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;

/**
 *
 * @author EVG_Adminer
 */
public class StrategyCCICorrection extends StrategyItem {

    public StrategyCCICorrection(StrategiesController controller) {
        super(controller);
        StrategyName = "CCICorrection";
        config.Add("CCI1-TimeFrame", new StrategyConfigItem("2", "19", "1", "5"));
        config.Add("CCI2-TimeFrame", new StrategyConfigItem("20", "200", "10", "200"));
    }

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        
        int short_tf = config.GetIntValue("CCI1-TimeFrame");
        int long_tf = config.GetIntValue("CCI2-TimeFrame");
        
        initializer = (tseries, trecord, dataset) -> {};
        
        CCIIndicator shortCci = new CCIIndicator(series, short_tf);
        CCIIndicator longCci = new CCIIndicator(series, long_tf);
        Decimal plus100 = Decimal.HUNDRED;
        Decimal minus100 = Decimal.valueOf(-100);
        
        Rule entryRule = new OverIndicatorRule(longCci, plus100) // Bull trend
                .and(new UnderIndicatorRule(shortCci, minus100)); // Signal
        
        Rule exitRule = new UnderIndicatorRule(longCci, minus100) // Bear trend
                .and(new OverIndicatorRule(shortCci, plus100)); // Signal
        
        Strategy strategy = new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
        strategy.setUnstablePeriod(5);
        return strategy;
    }
}
