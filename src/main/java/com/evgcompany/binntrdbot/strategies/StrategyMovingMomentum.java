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
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.StochasticOscillatorKIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.trading.rules.CrossedDownIndicatorRule;
import org.ta4j.core.trading.rules.CrossedUpIndicatorRule;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;

/**
 *
 * @author EVG_Adminer
 */
public class StrategyMovingMomentum extends StrategyItem {

    public StrategyMovingMomentum(StrategiesController controller) {
        super(controller);
        StrategyName = "MovingMomentum";
    }

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        initializer = (tseries, dataset) -> {
            ClosePriceIndicator closePrice = new ClosePriceIndicator(tseries);
            EMAIndicator shortEma = new EMAIndicator(closePrice, 5);
            EMAIndicator longEma = new EMAIndicator(closePrice, 26);
            dataset.addSeries(buildChartTimeSeries(tseries, shortEma, "EMA 5"));
            dataset.addSeries(buildChartTimeSeries(tseries, longEma, "EMA 26"));
        };
        
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        
        EMAIndicator shortEma = new EMAIndicator(closePrice, 5);
        EMAIndicator longEma = new EMAIndicator(closePrice, 26);

        StochasticOscillatorKIndicator stoK = new StochasticOscillatorKIndicator(series, 14);

        MACDIndicator macd = new MACDIndicator(closePrice, 5, 26);
        EMAIndicator emaMacd = new EMAIndicator(macd, 18);
        
        Rule entryRule = new OverIndicatorRule(shortEma, longEma)
                .and(new CrossedDownIndicatorRule(stoK, Decimal.valueOf(20)))
                .and(new OverIndicatorRule(macd, emaMacd));
        
        Rule exitRule = new UnderIndicatorRule(shortEma, longEma)
                .and(new CrossedUpIndicatorRule(stoK, Decimal.valueOf(80)))
                .and(new UnderIndicatorRule(macd, emaMacd));

        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
    }
}
