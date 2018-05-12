/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.events;

import com.evgcompany.binntrdbot.CoinBalanceItem;

/**
 *
 * @author EVG_Adminer
 */
@FunctionalInterface
public interface CoinChangeEvent {
    void onCoinUpdate(CoinBalanceItem coin);
}