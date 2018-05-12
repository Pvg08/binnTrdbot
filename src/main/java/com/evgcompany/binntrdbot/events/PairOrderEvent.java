/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.events;

import com.evgcompany.binntrdbot.OrderPairItem;
import java.math.BigDecimal;

/**
 *
 * @author EVG_Adminer
 */
@FunctionalInterface
public interface PairOrderEvent {
    void onOrderUpdate(
            Long orderCID, 
            OrderPairItem orderPair, 
            boolean isNew,          // order is NEW
            boolean isBuying,       // order is for BUY
            boolean isCancelled,    // order is cancelled
            boolean isFinished,     // order is finished (cancelled or filled)
            BigDecimal price,       // order Price
            BigDecimal qty,         // order original qty
            BigDecimal executedQty  // order executed qty
    );
}