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
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.MaxPriceIndicator;
import org.ta4j.core.indicators.helpers.MinPriceIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.trading.rules.BooleanRule;
import org.ta4j.core.trading.rules.CrossedDownIndicatorRule;
import org.ta4j.core.trading.rules.CrossedUpIndicatorRule;
import org.ta4j.core.trading.rules.InPipeRule;
import org.ta4j.core.trading.rules.IsFallingRule;
import org.ta4j.core.trading.rules.IsRisingRule;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;


/**
 *
 * @author EVG_Adminer
 */
public class StrategyBollinger extends StrategyItem {

    int mode = 1;
    
    public StrategyBollinger(StrategiesController controller, int mode) {
        super(controller);
        StrategyName = "Bollinger";
        if (mode == 0) mode = 1;
        if (mode > 3) mode = 3;
        if (mode > 1) {
            StrategyName += mode;
        }
        this.mode = mode;
    }

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        
        int length = 20;
        double mult = 2.0;
        
        initializer = (tseries, dataset) -> {
            ClosePriceIndicator indicator = new ClosePriceIndicator(tseries);
            EMAIndicator basis = new EMAIndicator(indicator, length);
            StandardDeviationIndicator dev = new StandardDeviationIndicator(indicator, length);
            BollingerBandsMiddleIndicator middleBBand = new BollingerBandsMiddleIndicator(basis);
            BollingerBandsLowerIndicator lowBBand = new BollingerBandsLowerIndicator(middleBBand, dev, Decimal.valueOf(mult));
            BollingerBandsUpperIndicator upBBand = new BollingerBandsUpperIndicator(middleBBand, dev, Decimal.valueOf(mult));
            dataset.addSeries(buildChartTimeSeries(tseries, middleBBand, "Middle Bollinger Band"));
            dataset.addSeries(buildChartTimeSeries(tseries, lowBBand, "Low Bollinger Band"));
            dataset.addSeries(buildChartTimeSeries(tseries, upBBand, "High Bollinger Band"));
        };
        
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        MinPriceIndicator minPrice = new MinPriceIndicator(series);
        MaxPriceIndicator maxPrice = new MaxPriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(closePrice, 2);
        
        EMAIndicator basis = new EMAIndicator(closePrice, length);
        StandardDeviationIndicator dev = new StandardDeviationIndicator(closePrice, length);
        
        BollingerBandsMiddleIndicator bollingerM = new BollingerBandsMiddleIndicator(basis);
        BollingerBandsLowerIndicator BBB = new BollingerBandsLowerIndicator(bollingerM, dev, Decimal.valueOf(mult));
        BollingerBandsUpperIndicator BBT = new BollingerBandsUpperIndicator(bollingerM, dev, Decimal.valueOf(mult));
        
        Rule entryRule;
        Rule exitRule;

        switch (mode) {
            case 1:
                entryRule = new CrossedDownIndicatorRule(BBB, minPrice)
                .and(new InPipeRule(rsi, Decimal.valueOf(55), Decimal.valueOf(0)))
                .and(new IsRisingRule(rsi, 1));
                exitRule = new CrossedUpIndicatorRule(BBT, maxPrice) 
                    .and(new InPipeRule(rsi, Decimal.valueOf(100), Decimal.valueOf(45)))
                    .and(new IsFallingRule(rsi, 1));
                break;
            case 2:
                entryRule = new OverIndicatorRule(closePrice, BBB)
                    .and(new UnderIndicatorRule(new PreviousValueIndicator(closePrice, 1), new PreviousValueIndicator(BBB, 1)));
                exitRule = new UnderIndicatorRule(closePrice, BBT)
                    .and(new OverIndicatorRule(new PreviousValueIndicator(closePrice, 1), new PreviousValueIndicator(BBT, 1)));
                break;
            case 3:
                entryRule = new CrossedUpIndicatorRule(closePrice, BBB);
                exitRule = new CrossedDownIndicatorRule(closePrice, BBT);
                break;
            default:
                exitRule = entryRule = new BooleanRule(false);
        }
        
        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
    }
    
}
