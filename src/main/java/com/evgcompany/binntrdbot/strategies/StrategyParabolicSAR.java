/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.strategies;

import com.evgcompany.binntrdbot.StrategiesController;
import com.evgcompany.binntrdbot.analysis.StrategyConfigItem;
import java.math.BigDecimal;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Decimal;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.ParabolicSarIndicator;
import org.ta4j.core.indicators.helpers.MaxPriceIndicator;
import org.ta4j.core.indicators.helpers.MinPriceIndicator;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;


/**
 *
 * @author EVG_Adminer
 */
public class StrategyParabolicSAR extends StrategyItem {

    public StrategyParabolicSAR(StrategiesController controller) {
        super(controller);
        StrategyName = "Parabolic SAR";
        config.Add("PSAR-aF", new StrategyConfigItem("0.005", "0.05", "0.005", "0.02"));
        config.Add("PSAR-maxA", new StrategyConfigItem("0.1", "1.0", "0.1", "0.2"));
        config.Add("PSAR-incr", new StrategyConfigItem("0.005", "0.05", "0.005", "0.02"));
    }

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        
        BigDecimal aF = config.GetValue("PSAR-aF");
        BigDecimal maxA = config.GetValue("PSAR-maxA");
        BigDecimal incr = config.GetValue("PSAR-incr");
        
        initializer = (tseries, dataset) -> {
            ParabolicSarIndicator psar = new ParabolicSarIndicator(series, Decimal.valueOf(aF), Decimal.valueOf(maxA), Decimal.valueOf(incr));
            dataset.addSeries(buildChartTimeSeries(tseries, psar, "Parabolic SAR" + config.toString()));
        };
        
        MaxPriceIndicator maxPrice = new MaxPriceIndicator(series);
        MinPriceIndicator minPrice = new MinPriceIndicator(series);
        ParabolicSarIndicator psar = new ParabolicSarIndicator(series, Decimal.valueOf(aF), Decimal.valueOf(maxA), Decimal.valueOf(incr));
        
        Rule entryRule = new OverIndicatorRule(psar, maxPrice);
        
        Rule exitRule = new UnderIndicatorRule(psar, minPrice);
        
        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
    }
}
