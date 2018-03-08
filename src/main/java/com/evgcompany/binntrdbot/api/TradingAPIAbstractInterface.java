/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.api;

import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.general.RateLimit;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.market.TickerPrice;
import java.math.BigDecimal;
import java.util.List;
import org.ta4j.core.Bar;

/**
 *
 * @author EVG_adm_T
 */
public abstract class TradingAPIAbstractInterface {
    String secret, key;
    
    public TradingAPIAbstractInterface(String secret, String key) {
        this.secret = secret;
        this.key = key;
    }
    
    abstract public boolean connect();
    abstract public boolean disconnect();
    
    abstract public long getServerTime();
    abstract public long getAlignedCurrentTimeMillis();
    
    abstract public List<RateLimit> getRateLimits();
    abstract public List<Bar> getBars(String pair, int count);
    abstract public SymbolInfo getSymbolInfo();
    abstract public List<TickerPrice> getAllPrices();
    
    abstract public long order(boolean is_buy, boolean is_market, String pair, BigDecimal price, BigDecimal amount);
    abstract public Order getOrderStatus(String pair, long order_id);
}
