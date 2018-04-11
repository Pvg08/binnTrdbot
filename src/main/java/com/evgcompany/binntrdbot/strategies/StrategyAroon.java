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
import org.ta4j.core.indicators.AroonDownIndicator;
import org.ta4j.core.indicators.AroonUpIndicator;
import org.ta4j.core.indicators.helpers.MinPriceIndicator;
import org.ta4j.core.indicators.helpers.MaxPriceIndicator;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;

/**
 *
 * @author EVG_Adminer
 */
public class StrategyAroon extends StrategyItem {

    public StrategyAroon(StrategiesController controller) {
        super(controller);
        StrategyName = "Aroon";
        config.Add("Aroon-TimeFrame", new StrategyConfigItem("5", "50", "5", "25"));
        config.Add("Aroon-Threshold1", new StrategyConfigItem("1", "40", "1", "20"));
        config.Add("Aroon-Threshold2", new StrategyConfigItem("60", "99", "1", "70"));
    }

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        int timeFrame = config.GetIntValue("Aroon-TimeFrame");
        int threshold1 = config.GetIntValue("Aroon-Threshold1");
        int threshold2 = config.GetIntValue("Aroon-Threshold2");
        initializer = (tseries, dataset) -> {};
        MinPriceIndicator minPrice = new MinPriceIndicator(series);
        MaxPriceIndicator maxPrice = new MaxPriceIndicator(series);
        AroonUpIndicator aup = new AroonUpIndicator(series, maxPrice, timeFrame);
        AroonDownIndicator adn = new AroonDownIndicator(series, minPrice, timeFrame);

        Rule entryRule = new UnderIndicatorRule(aup, Decimal.valueOf(threshold1))
                .and(new UnderIndicatorRule(adn, Decimal.valueOf(threshold1)));

        Rule exitRule = new OverIndicatorRule(aup, Decimal.valueOf(threshold2))
                .and(new OverIndicatorRule(adn, Decimal.valueOf(threshold2)));

        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
    }
}
