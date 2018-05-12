/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.events;

import com.binance.api.client.domain.account.AssetBalance;
import java.util.List;

/**
 *
 * @author EVG_Adminer
 */
@FunctionalInterface
public interface BalanceEvent {
    void onUpdate(List<AssetBalance> assetBalances);
}