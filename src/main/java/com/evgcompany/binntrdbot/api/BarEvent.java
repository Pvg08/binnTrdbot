/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.api;

import org.ta4j.core.Bar;

/**
 *
 * @author EVG_Adminer
 */
@FunctionalInterface
public interface BarEvent {
    void onUpdate(Bar last_bar, boolean is_closed);
}