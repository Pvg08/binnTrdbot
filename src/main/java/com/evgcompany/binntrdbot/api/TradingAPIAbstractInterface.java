/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.api;

import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.Trade;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.RateLimit;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.market.TickerPrice;
import java.io.Closeable;
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
    
    public long getServerTime() {
        return System.currentTimeMillis();
    }
    
    public long getAlignedCurrentTimeMillis() {
        return System.currentTimeMillis();
    }
    
    public int barIntervalToSeconds(String _barInterval) {
        int newSeconds = 0;
        switch (_barInterval) {
            case "1m":
                newSeconds = 60;
                break;
            case "5m":
                newSeconds = 60 * 5;
                break;
            case "15m":
                newSeconds = 60 * 15;
                break;
            case "30m":
                newSeconds = 60 * 30;
                break;
            case "1h":
                newSeconds = 1 * 60 * 60;
                break;
            case "2h":
                newSeconds = 2 * 60 * 60;
                break;
            case "3h":
                newSeconds = 3 * 60 * 60;
                break;
            case "4h":
                newSeconds = 4 * 60 * 60;
                break;
            case "6h":
                newSeconds = 6 * 60 * 60;
                break;
            case "8h":
                newSeconds = 8 * 60 * 60;
                break;
            case "12h":
                newSeconds = 12 * 60 * 60;
                break;
            case "1d":
                newSeconds = 24 * 60 * 60;
                break;
            default:
                break;
        }
        return newSeconds;
    }
    
    abstract public List<RateLimit> getRateLimits();
    abstract public List<Bar> getBars(String pair, String bar_interval);
    abstract public List<Bar> getBars(String pair, String bar_interval, int count, long millis_from, long millis_to);
    abstract public ExchangeInfo getExchangeInfo();
    abstract public SymbolInfo getSymbolInfo(String symbol);
    abstract public List<TickerPrice> getAllPrices();
    abstract public List<AssetBalance> getAllBalances();
    abstract public AssetBalance getAssetBalance(String pair);
    
    abstract public long order(boolean is_buy, boolean is_market, String pair, BigDecimal amount, BigDecimal price);
    abstract public Order getOrderStatus(String pair, long order_id);
    abstract public void cancelOrder(String pair, long order_id);
    
    abstract public Trade getLastTrade(String pair);
    abstract public List<Order> getOpenOrders(String pair);
    
    abstract public BigDecimal getLastTradePrice(String symbolPair, boolean isBuyer);
    abstract public BigDecimal getCurrentPrice(String symbolPair);
    
    abstract public Closeable OnBarUpdateEvent(String pair, String barInterval, BarEvent evt);
}
