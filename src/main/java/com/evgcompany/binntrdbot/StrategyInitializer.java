/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import org.ta4j.core.Strategy;
import org.ta4j.core.TimeSeries;

/**
 *
 * @author EVG_Adminer
 */
@FunctionalInterface
public interface StrategyInitializer {
    Strategy onInit(TimeSeries series);
}