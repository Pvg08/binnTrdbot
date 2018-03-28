/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.strategies;

import com.evgcompany.binntrdbot.StrategiesController;
import com.evgcompany.binntrdbot.analysis.WhenCheckCondition;
import com.evgcompany.binntrdbot.analysis.WhenValueIndicator;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Decimal;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.MedianPriceIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;

/**
 *
 * @author EVG_Adminer
 */
public class StrategyFractalBreakout extends StrategyItem {

    public StrategyFractalBreakout(StrategiesController controller) {
        super(controller);
        StrategyName = "Fractal Breakout";
    }

    /*
        // fractal calculation
        price = (high + low) / 2
        fractal_top = high[2] > high[3] and high[2] > high[4] and high[2] > high[1] and high[2] > high[0]
        fractal_price = valuewhen(fractal_top, price, 1)
        fractal_average = (fractal_price[1] + fractal_price[2] + fractal_price[3] ) / 3
        fractal_trend = fractal_average[0] > fractal_average[1]
        fractal_breakout = price[1] > fractal_price[0]

        // strategy

        trade_entry = fractal_trend and fractal_breakout
        trade_exit = fractal_trend[n_time] and fractal_trend == false 
    */

    @Override
    public Strategy buildStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        
        int n_time = 5;
        
        WhenCheckCondition fractalTop = (rseries, bar_index) -> {
            if ((bar_index - 4) < rseries.getBeginIndex()) {
                return false;
            }
            double high_0 = rseries.getBar(bar_index).getMaxPrice().doubleValue();
            double high_1 = rseries.getBar(bar_index-1).getMaxPrice().doubleValue();
            double high_2 = rseries.getBar(bar_index-2).getMaxPrice().doubleValue();
            double high_3 = rseries.getBar(bar_index-3).getMaxPrice().doubleValue();
            double high_4 = rseries.getBar(bar_index-4).getMaxPrice().doubleValue();
            return ((high_2 > high_3) && (high_2 > high_4) && (high_2 > high_1) && (high_2 > high_0));
        };
        
        initializer = (tseries, dataset) -> {
            MedianPriceIndicator price = new MedianPriceIndicator(tseries);
            WhenValueIndicator fractalPrice = new WhenValueIndicator(price, Decimal.ZERO, 1, fractalTop);
            SMAIndicator fractalAverage = new SMAIndicator(fractalPrice, 3);
            dataset.addSeries(buildChartTimeSeries(tseries, fractalAverage, "Fractal Average"));
        };

        MedianPriceIndicator price = new MedianPriceIndicator(series);
        WhenValueIndicator fractalPrice = new WhenValueIndicator(price, Decimal.ZERO, 1, fractalTop);
        SMAIndicator fractalAverage = new SMAIndicator(fractalPrice, 3);
        
        Rule entryRule = new OverIndicatorRule(new PreviousValueIndicator(fractalAverage, 1), new PreviousValueIndicator(fractalAverage, 2))
                .and(new OverIndicatorRule(new PreviousValueIndicator(price, 1), fractalPrice));
        
        Rule exitRule = new OverIndicatorRule(new PreviousValueIndicator(fractalAverage, n_time + 1), new PreviousValueIndicator(fractalAverage, n_time+2))
                .and(new UnderIndicatorRule(new PreviousValueIndicator(fractalAverage, 1), new PreviousValueIndicator(fractalAverage, 2)));
        
        return new BaseStrategy(entryRule, addStopLossGain(exitRule, series));
    }
    
}
