/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.strategies;

import com.evgcompany.binntrdbot.StrategiesController;
import com.evgcompany.binntrdbot.analysis.StrategyConfigItem;
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
        config.Add("CMF-TimeFrame", new StrategyConfigItem("3", "25", "2", "7"));
        config.Add("CMF-Threshold", new StrategyConfigItem("0.01", "0.2", "0.01", "0.05"));
        config.Add("RSI-TimeFrame", new StrategyConfigItem("2", "10", "2", "4"));
    }

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        
        int cmf_tf = config.GetIntValue("CMF-TimeFrame");
        int rsi_tf = config.GetIntValue("RSI-TimeFrame");
        double cmf_ts = config.GetDoubleValue("CMF-Threshold");
        
        initializer = (tseries, dataset) -> {};
        
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        ChaikinMoneyFlowIndicator cmf = new ChaikinMoneyFlowIndicator(series, cmf_tf);
        RSIIndicator rsi = new RSIIndicator(closePrice, rsi_tf);

        Rule entryRule = new CrossedDownIndicatorRule(cmf, Decimal.valueOf(cmf_ts))
                .and(new UnderIndicatorRule(rsi, Decimal.valueOf(50)));
        
        Rule exitRule = new CrossedUpIndicatorRule(cmf, Decimal.valueOf(-cmf_ts))
                .and(new OverIndicatorRule(rsi, Decimal.valueOf(50)));
        
        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
    }
}
