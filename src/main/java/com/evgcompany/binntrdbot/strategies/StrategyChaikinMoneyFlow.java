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
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.volume.ChaikinMoneyFlowIndicator;
import org.ta4j.core.trading.rules.CrossedDownIndicatorRule;
import org.ta4j.core.trading.rules.CrossedUpIndicatorRule;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;


/**
 *
 * @author EVG_Adminer
 */
public class StrategyChaikinMoneyFlow extends StrategyItem {

    public StrategyChaikinMoneyFlow(StrategiesController controller) {
        super(controller);
        StrategyName = "Chaikin Money Flow";
    }

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        initializer = (tseries, dataset) -> {};
        
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        ChaikinMoneyFlowIndicator cmf = new ChaikinMoneyFlowIndicator(series, 7);
        RSIIndicator rsi = new RSIIndicator(closePrice, 4);

        Rule entryRule = new CrossedDownIndicatorRule(cmf, Decimal.valueOf(0.05))
                .and(new UnderIndicatorRule(rsi, Decimal.valueOf(50)));
        
        Rule exitRule = new CrossedUpIndicatorRule(cmf, Decimal.valueOf(-0.05))
                .and(new OverIndicatorRule(rsi, Decimal.valueOf(50)));
        
        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
    }
}
