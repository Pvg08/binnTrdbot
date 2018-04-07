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
    }

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        initializer = (tseries, dataset) -> {};
        
        MinPriceIndicator minPrice = new MinPriceIndicator(series);
        MaxPriceIndicator maxPrice = new MaxPriceIndicator(series);
        AroonUpIndicator aup = new AroonUpIndicator(series, maxPrice, 25);
        AroonDownIndicator adn = new AroonDownIndicator(series, minPrice, 25);

        Rule entryRule = new UnderIndicatorRule(aup, Decimal.valueOf(20))
                .and(new UnderIndicatorRule(adn, Decimal.valueOf(20)));

        Rule exitRule = new OverIndicatorRule(aup, Decimal.valueOf(70))
                .or(new OverIndicatorRule(adn, Decimal.valueOf(70)));

        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
    }
    
}
