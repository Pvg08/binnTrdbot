/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.events;

/**
 *
 * @author EVG_adm_T
 */
@FunctionalInterface
public interface MarketcapGlobalDataUpdateEvent {
    void onUpdate(double vol24, double marketcap, double btc_dominance_percent);
}