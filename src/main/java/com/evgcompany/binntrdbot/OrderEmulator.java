/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.OrderType;
import com.binance.api.client.domain.account.Order;
import com.evgcompany.binntrdbot.coinrating.CoinInfoAggregator;
import com.evgcompany.binntrdbot.misc.NumberFormatter;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author EVG_adm_T
 */
public class OrderEmulator {
    
    private final Map<Long, Order> orders = new HashMap<>();
    private long orderIdCounter = 0;
    
    private String numberFormatForOrder(BigDecimal num) {
        return NumberFormatter.df8.format(num).replace(".","").replace(",",".").replace(" ","");
    }
    
    public long emulateOrder(boolean is_buy, boolean is_market, String pair, BigDecimal amount, BigDecimal price) {
        Order order = new Order();
        order.setSymbol(pair);
        order.setStatus(OrderStatus.NEW);
        order.setSide(is_buy ? OrderSide.BUY : OrderSide.SELL);
        order.setType(is_market ? OrderType.MARKET : OrderType.LIMIT);
        order.setExecutedQty("0");
        order.setOrigQty(numberFormatForOrder(amount));
        order.setPrice(numberFormatForOrder(price));
        order.setOrderId(++orderIdCounter);
        orders.put(order.getOrderId(), order);
        return order.getOrderId();
    }
    
    public Order getEmulatedOrder(long orderID) {
        return orders.get(orderID);
    }
    
    private void addOrderExecutedQty(Order order, double percentage) {
        BigDecimal price = new BigDecimal(order.getPrice());
        BigDecimal qty = new BigDecimal(order.getOrigQty());
        BigDecimal prev_executed = new BigDecimal(order.getExecutedQty());
        BigDecimal executed = new BigDecimal(order.getExecutedQty());
        executed = executed.add(qty.multiply(BigDecimal.valueOf(percentage)));
        if (executed.compareTo(qty) >= 0) {
            executed = qty;
            order.setStatus(OrderStatus.FILLED);
        } else {
            order.setStatus(OrderStatus.PARTIALLY_FILLED);
        }
        order.setExecutedQty(numberFormatForOrder(executed));
    }
    
    public void progressOrder(long orderID) {
        Order order = orders.get(orderID);
        if (order == null || order.getStatus() == OrderStatus.CANCELED || order.getStatus() == OrderStatus.FILLED) {
            return;
        }
        boolean isBuying = order.getSide() == OrderSide.BUY;
        BigDecimal orderPrice = new BigDecimal(order.getPrice());
        BigDecimal currentPrice = BigDecimal.valueOf(CoinInfoAggregator.getInstance().getLastPrices().get(order.getSymbol()));
        
        if (order.getType() == OrderType.MARKET) {
            order.setPrice(numberFormatForOrder(currentPrice));
            addOrderExecutedQty(order, 100.0000001);
        } else {
            currentPrice = currentPrice.multiply(BigDecimal.valueOf((isBuying ? 1.00001 : 0.99999)));
            if (isBuying) {
                if (currentPrice.compareTo(orderPrice) <= 0) {
                    addOrderExecutedQty(order, Math.random() * 200);
                }
            } else {
                if (currentPrice.compareTo(orderPrice) >= 0) {
                    addOrderExecutedQty(order, Math.random() * 200);
                }
            }
        }
    }
    
    public void cancelOrder(long orderID) {
        Order order = orders.get(orderID);
        if (order == null) return;
        order.setStatus(OrderStatus.CANCELED);
    }
}
