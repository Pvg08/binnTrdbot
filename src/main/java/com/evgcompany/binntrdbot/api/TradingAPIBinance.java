/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.api;

import com.binance.api.client.BinanceApiCallback;
import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.Account;
import com.binance.api.client.domain.account.AssetBalance;
import static com.binance.api.client.domain.account.NewOrder.*;
import com.binance.api.client.domain.account.NewOrderResponse;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.Trade;
import com.binance.api.client.domain.account.request.AllOrdersRequest;
import com.binance.api.client.domain.account.request.CancelOrderRequest;
import com.binance.api.client.domain.account.request.OrderRequest;
import com.binance.api.client.domain.account.request.OrderStatusRequest;
import com.binance.api.client.domain.event.CandlestickEvent;
import com.binance.api.client.domain.event.OrderTradeUpdateEvent;
import com.binance.api.client.domain.event.UserDataUpdateEvent;
import com.binance.api.client.domain.event.UserDataUpdateEvent.UserDataUpdateEventType;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.RateLimit;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.domain.market.TickerPrice;
import com.binance.api.client.domain.market.TickerStatistics;
import com.evgcompany.binntrdbot.events.BalanceEvent;
import com.evgcompany.binntrdbot.events.BarEvent;
import com.evgcompany.binntrdbot.events.OrderEvent;
import com.evgcompany.binntrdbot.events.SocketClosedEvent;
import com.fasterxml.jackson.core.JsonParseException;
import java.io.Closeable;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.Decimal;

/**
 *
 * @author EVG_Adminer
 */
public class TradingAPIBinance extends TradingAPIAbstractInterface {

    private BinanceApiClientFactory factory = null;
    private BinanceApiRestClient client = null;
    private BinanceApiWebSocketClient client_s = null;
    

    public TradingAPIBinance(String secret, String key) {
        super(secret, key);
        tradeComissionPercent = new BigDecimal("0.1");
        tradeComissionCurrencyPercent = new BigDecimal("0.075");
        tradeComissionCurrency = "BNB";
    }

    @Override
    public boolean connect() {
        if (factory == null || client == null) {
            factory = BinanceApiClientFactory.newInstance(
                key,
                secret
            );
            client = factory.newRestClient();
        }
        return client != null;
    }

    @Override
    public boolean disconnect() {
        client = null;
        factory = null;
        return true;
    }

    @Override
    public long getServerTime() {
        return client.getServerTime();
    }

    @Override
    public long getAlignedCurrentTimeMillis() {
        return System.currentTimeMillis();
    }

    @Override
    public List<RateLimit> getRateLimits() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private void addBarsToList(List<Candlestick> nbars, List<Bar> result, String bar_interval) {
        Duration barDuration = Duration.ofSeconds(barIntervalToSeconds(bar_interval));
        for(int i=0; i<nbars.size(); i++) {
            Candlestick stick = nbars.get(i);
            ZonedDateTime endTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(stick.getCloseTime()), ZoneId.systemDefault());
            Bar newbar = new BaseBar(barDuration, endTime, Decimal.valueOf(stick.getOpen()), Decimal.valueOf(stick.getHigh()), Decimal.valueOf(stick.getLow()), Decimal.valueOf(stick.getClose()), Decimal.valueOf(stick.getVolume()), Decimal.valueOf(stick.getQuoteAssetVolume()));
            result.add(newbar);
        }
    }
    
    private CandlestickInterval getCandlestickIntervalByString(String interval) {
        CandlestickInterval[] arr = CandlestickInterval.values();
        for (CandlestickInterval arr1 : arr) {
            if (arr1.getIntervalId().equals(interval)) {
                return arr1;
            }
        } 
        return null;
    }
    
    @Override
    public List<Bar> getBars(String pair, String bar_interval) {
        return getBars(pair, bar_interval, null, null, null);
    }

    @Override
    public List<Bar> getBars(String pair, String bar_interval, Integer bar_count, Long millis_from, Long millis_to) {
        CandlestickInterval interval = getCandlestickIntervalByString(bar_interval);
        List<Candlestick> nbars = client.getCandlestickBars(pair, interval, bar_count, millis_from, millis_to);
        List<Bar> result = new ArrayList<>(0);
        addBarsToList(nbars, result, bar_interval);
        return result;
    }
    
    @Override
    public ExchangeInfo getExchangeInfo() {
        return client.getExchangeInfo();
    }
    
    @Override
    public SymbolInfo getSymbolInfo(String symbol) {
        ExchangeInfo info = client.getExchangeInfo();
        SymbolInfo pair_sinfo = info.getSymbolInfo(symbol);
        return pair_sinfo;
    }

    @Override
    public List<TickerPrice> getAllPrices() {
        List<TickerPrice> allPrices = client.getAllPrices();
        return allPrices;
    }

    @Override
    public List<AssetBalance> getAllBalances() {
        Account account = client.getAccount();
        List<AssetBalance> allBalances = account.getBalances();
        return allBalances;
    }
    
    @Override
    public AssetBalance getAssetBalance(String pair) {
        Account account = client.getAccount();
        return account.getAssetBalance(pair);
    }
    
    @Override
    public Long order(boolean is_buy, boolean is_market, String pair, BigDecimal amount, BigDecimal price) {
        NewOrderResponse newOrderResponse;
        if (is_buy) {
            if (is_market) {
                newOrderResponse = client.newOrder(marketBuy(pair, numberFormatForOrder(amount)));
            } else {
                newOrderResponse = client.newOrder(limitBuy(pair, TimeInForce.GTC, numberFormatForOrder(amount), numberFormatForOrder(price)));
            }
        } else {
            if (is_market) {
                newOrderResponse = client.newOrder(marketSell(pair, numberFormatForOrder(amount)));
            } else {
                newOrderResponse = client.newOrder(limitSell(pair, TimeInForce.GTC, numberFormatForOrder(amount), numberFormatForOrder(price)));
            }
        }
        return (newOrderResponse != null) ? newOrderResponse.getOrderId() : null;
    }

    private String realPriceOfOrder(String symbol, long order_id) {
        double sumQty = 0;
        double sumCost = 0;
        List<Trade> myTrades = client.getMyTrades(symbol);
        for (int i=0; i < myTrades.size(); i++) {
            if (Long.parseLong(myTrades.get(i).getOrderId()) == order_id) {
                double qty = Double.parseDouble(myTrades.get(i).getQty());
                double price = Double.parseDouble(myTrades.get(i).getPrice());
                sumQty += qty;
                sumCost += price*qty;
            }
        }
        return sumQty > 0 ? numberFormatForOrder(sumCost/sumQty) : "0";
    }
    
    @Override
    public Order getOrderStatus(String symbol, long order_id) throws JsonParseException {
        Order order = client.getOrderStatus(new OrderStatusRequest(symbol, order_id));
        if (order != null && Double.parseDouble(order.getPrice()) <= 0) {
            order.setPrice(realPriceOfOrder(symbol, order_id));
        }
        return order;
    }

    @Override
    public void cancelOrder(String pair, long order_id) {
        client.cancelOrder(new CancelOrderRequest(pair, order_id));
    }

    @Override
    public Trade getLastTrade(String pair) {
        int i, imax = -1;
        long max_buy_time = 0;
        List<Trade> myTrades = client.getMyTrades(pair);
        for (i=0; i < myTrades.size(); i++) {
            if (myTrades.get(i).isBuyer() && max_buy_time < myTrades.get(i).getTime()) {
                max_buy_time = myTrades.get(i).getTime();
                imax = i;
            }
        }
        if (imax >= 0) {
            return myTrades.get(imax);
        }
        return null;
    }

    @Override
    public List<Order> getOpenOrders(String pair) {
        return client.getOpenOrders(new OrderRequest(pair));
    }

    @Override
    public BigDecimal getLastTradePrice(String symbolPair, boolean isBuyer) {
        int i, imax = -1;
        long max_buy_time = 0;
        List<Trade> myTrades = client.getMyTrades(symbolPair);
        for (i=0; i < myTrades.size(); i++) {
            if (myTrades.get(i).isBuyer() == isBuyer && max_buy_time < myTrades.get(i).getTime()) {
                max_buy_time = myTrades.get(i).getTime();
                imax = i;
            }
        }
        if (imax >= 0) {
            return new BigDecimal(myTrades.get(imax).getPrice());
        }
        return BigDecimal.ZERO;
    }

    @Override
    public List<Trade> getAllTrades(String pair) {
        return client.getMyTrades(pair);
    }

    @Override
    public List<Trade> getOrderHistory(String symbol, int limit) {
        return client.getMyTrades(symbol, limit);
    }
    
    @Override
    public BigDecimal getCurrentPrice(String symbolPair) {
        TickerStatistics tc = client.get24HrPriceStatistics(symbolPair);
        return tc != null ? (new BigDecimal(tc.getLastPrice())) : null;
    }

    @Override
    public Closeable OnBarUpdateEvent(String symbol, String barInterval, BarEvent evt, SocketClosedEvent onClosed) {
        if (client_s == null) {
            client_s = factory.newWebSocketClient();
        }
        
        BinanceApiCallback<CandlestickEvent> event = new BinanceApiCallback<CandlestickEvent>() {
            @Override
            public void onResponse(CandlestickEvent response) {
                Duration barDuration = Duration.ofSeconds(barIntervalToSeconds(barInterval));
                ZonedDateTime endTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(response.getCloseTime()), ZoneId.systemDefault());
                Bar nbar = new BaseBar(
                        barDuration, 
                        endTime, 
                        Decimal.valueOf(response.getOpen()), 
                        Decimal.valueOf(response.getHigh()), 
                        Decimal.valueOf(response.getLow()), 
                        Decimal.valueOf(response.getClose()), 
                        Decimal.valueOf(response.getVolume()), 
                        Decimal.valueOf(response.getQuoteAssetVolume())
                );
                evt.onUpdate(nbar, response.getBarFinal());
            }
            
            @Override
            public void onFailure(Throwable cause) {
                if (onClosed != null) onClosed.onClosed(cause.toString());
            }

            @Override
            public void onClosed(String reason) {
                if (onClosed != null) onClosed.onClosed(reason);
            }
        };
        
        return client_s.onCandlestickEvent(symbol.toLowerCase(), getCandlestickIntervalByString(barInterval), event);
    }
    
    @Override
    public Closeable OnOrderEvent(String symbol, OrderEvent evt, SocketClosedEvent onClosed) {
        if (client_s == null) {
            client_s = factory.newWebSocketClient();
        }
        String listenKey = client.startUserDataStream();
        
        BinanceApiCallback<UserDataUpdateEvent> event = new BinanceApiCallback<UserDataUpdateEvent>() {
            @Override
            public void onResponse(UserDataUpdateEvent response) {
                OrderTradeUpdateEvent event = response.getOrderTradeUpdateEvent();
                if (response.getEventType() == UserDataUpdateEventType.ORDER_TRADE_UPDATE) {
                    System.out.println(event);
                }
                if (
                        response.getEventType() == UserDataUpdateEventType.ORDER_TRADE_UPDATE &&
                        event.getEventType().equals("executionReport") && 
                        event.getOrderId() > 0 && (
                            symbol == null ||
                            event.getSymbol().toUpperCase().equals(symbol)
                        )
                ) {
                    if (
                            event.getOrderId() > 0 &&
                            Double.parseDouble(event.getPrice()) <= 0 &&
                            (
                                event.getOrderStatus() == OrderStatus.FILLED ||
                                event.getOrderStatus() == OrderStatus.CANCELED
                            )
                    ) {
                        event.setPrice(realPriceOfOrder(event.getSymbol(), event.getOrderId()));
                    }
                    evt.onUpdate(event);
                }
            }
            
            @Override
            public void onFailure(Throwable cause) {
                if (onClosed != null) onClosed.onClosed(cause.toString());
            }

            @Override
            public void onClosed(String reason) {
                if (onClosed != null) onClosed.onClosed(reason);
            }
        };
        
        return client_s.onUserDataUpdateEvent(listenKey, event);
    }
    
    @Override
    public Closeable OnBalanceEvent(BalanceEvent evt, SocketClosedEvent onClosed) {
        if (client_s == null) {
            client_s = factory.newWebSocketClient();
        }
        String listenKey = client.startUserDataStream();
        
        BinanceApiCallback<UserDataUpdateEvent> event = new BinanceApiCallback<UserDataUpdateEvent>() {
            @Override
            public void onResponse(UserDataUpdateEvent response) {
                if (response.getEventType() == UserDataUpdateEventType.ACCOUNT_UPDATE) {
                    evt.onUpdate(response.getAccountUpdateEvent().getBalances());
                }
            }
            
            @Override
            public void onFailure(Throwable cause) {
                if (onClosed != null) onClosed.onClosed(cause.toString());
            }

            @Override
            public void onClosed(String reason) {
                if (onClosed != null) onClosed.onClosed(reason);
            }
        };
        
        return client_s.onUserDataUpdateEvent(listenKey, event);
    }
}
