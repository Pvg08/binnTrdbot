/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.analysis;

import org.ta4j.core.Decimal;
import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.CachedIndicator;

/**
 *
 * @author EVG_Adminer
 */
public class WhenValueIndicator extends CachedIndicator<Decimal> {
    private int n;
    private Indicator<Decimal> indicator;
    private WhenCheckCondition check;
    private Decimal notfound;

    public WhenValueIndicator(Indicator<Decimal> indicator, Decimal notfound, int n, WhenCheckCondition check){
        super(indicator);
        this.n = n;
        this.check = check;
        this.indicator = indicator;
        this.notfound = notfound;
    }

    protected Decimal calculate(int index) {
        int k = 0;
        TimeSeries series = indicator.getTimeSeries();
        for(int i=index; i>=series.getBeginIndex(); i--) {
            if (check.onCheck(series, i)) {
                if (n == k) {
                    return indicator.getValue(i);
                }
                k++;
            }
        }
        return notfound;
    }
}
