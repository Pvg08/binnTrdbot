/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.analysis;

import org.ta4j.core.Decimal;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;

/**
 *
 * @author EVG_adm_T
 */
public class OHLC4Indicator extends CachedIndicator<Decimal> {

    private TimeSeries series;

    public OHLC4Indicator(TimeSeries series) {
        super(series);
        this.series = series;
    }

    @Override
    protected Decimal calculate(int index) {
        return series.getBar(index).getMaxPrice()
                .plus(series.getBar(index).getMinPrice())
                .plus(series.getBar(index).getOpenPrice())
                .plus(series.getBar(index).getClosePrice())
                .dividedBy(Decimal.valueOf(4));
    }
}