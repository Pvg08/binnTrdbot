/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.strategies;

import com.evgcompany.binntrdbot.strategies.core.*;
import com.evgcompany.binntrdbot.analysis.*;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.KAMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.trading.rules.CrossedDownIndicatorRule;
import org.ta4j.core.trading.rules.CrossedUpIndicatorRule;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;

/**
 *
 * @author EVG_Adminer
 */
public class StrategyKAMA extends StrategyItem {

    public StrategyKAMA(StrategiesController controller) {
        super(controller);
        StrategyName = "KAMA";
        config.Add("KAMA-TimeFrameFast1", new StrategyConfigItem("2", "25", "1", "2"));
        config.Add("KAMA-TimeFrameFast2", new StrategyConfigItem("2", "25", "1", "5"));
        config.Add("KAMA-TimeFrameSlow", new StrategyConfigItem("30", "50", "5", "30"));
        config.Add("KAMA-TimeFrameRatio", new StrategyConfigItem("5", "30", "5", "10"));
    }

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        
        int kama1_tf_fast = config.GetIntValue("KAMA-TimeFrameFast1");
        int kama2_tf_fast = config.GetIntValue("KAMA-TimeFrameFast2");
        int kama_tf_slow = config.GetIntValue("KAMA-TimeFrameSlow");
        int kama_tf_ratio = config.GetIntValue("KAMA-TimeFrameRatio");
        
        initializer = (tseries, dataset) -> {
            ClosePriceIndicator closePrice = new ClosePriceIndicator(tseries);
            KAMAIndicator kama2 = new KAMAIndicator(closePrice, kama_tf_ratio, kama1_tf_fast, kama_tf_slow);
            KAMAIndicator kama5 = new KAMAIndicator(closePrice, kama_tf_ratio, kama2_tf_fast, kama_tf_slow);
            dataset.addSeries(buildChartTimeSeries(tseries, kama2, "KAMA " + kama1_tf_fast));
            dataset.addSeries(buildChartTimeSeries(tseries, kama5, "KAMA " + kama2_tf_fast));
        };

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        KAMAIndicator kama1 = new KAMAIndicator(closePrice, kama_tf_ratio, kama1_tf_fast, kama_tf_slow);
        KAMAIndicator kama2 = new KAMAIndicator(closePrice, kama_tf_ratio, kama2_tf_fast, kama_tf_slow);
        
        Rule entryRule = new UnderIndicatorRule(kama2, kama1)
                .and(new CrossedUpIndicatorRule(kama1, closePrice));
        
        Rule exitRule = new OverIndicatorRule(kama2, kama1)
                .and(new CrossedDownIndicatorRule(kama1, closePrice));
        
        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
    }
    
}
