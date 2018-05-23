/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.strategies.core;

/**
 *
 * @author EVG_adm_T
 */
public enum OrderActionType {
    DO_NOTHING, 
    DO_STRATEGY_ENTER,
    DO_STRATEGY_EXIT,
    DO_STRATEGY_ENTER_SECONDARY,
    DO_STRATEGY_EXIT_SECONDARY,
    DO_SIGNAL_START,
    DO_BUY,
    DO_SELL
}
