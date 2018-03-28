/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.analysis;

import org.jfree.data.time.TimeSeriesCollection;
import org.ta4j.core.TimeSeries;

@FunctionalInterface
public interface WhenCheckCondition {
    boolean onCheck(TimeSeries series, int bar_index);
}
