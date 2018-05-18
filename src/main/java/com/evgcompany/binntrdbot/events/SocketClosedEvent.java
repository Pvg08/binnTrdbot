/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.events;

/**
 *
 * @author EVG_Adminer
 */
@FunctionalInterface
public interface SocketClosedEvent {
    void onClosed(String reason);
}