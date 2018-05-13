/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import com.evgcompany.binntrdbot.coinrating.CoinInfoAggregator;
import com.evgcompany.binntrdbot.events.PairOrderEvent;
import com.evgcompany.binntrdbot.misc.NumberFormatter;
import java.math.BigDecimal;

/**
 *
 * @author EVG_Adminer
 */
public class OrderPairItem {
    private String symbolBase = "";
    private String symbolPair = "";
    private String symbolQuote = "";
    private CoinBalanceItem base_item = null;
    private CoinBalanceItem quote_item = null;
    
    private BigDecimal summOrderBase = BigDecimal.ZERO;
    private BigDecimal summOrderQuote = BigDecimal.ZERO;
    
    private boolean in_order_buy_sell_cycle;
    private boolean order_pending;
    private boolean in_sell_order = false;
    private BigDecimal last_order_price = BigDecimal.ZERO;
    private BigDecimal last_order_amount = BigDecimal.ZERO;

    private String marker = "";
    
    private int listIndex = -1;
    private PairOrderEvent orderEvent = null;
    private ControllableOrderProcess orderProcess = null;
    
    private long orderAPIID = 0;
    
    private String lastCheckHashOrder = "";
    
    public OrderPairItem(CoinBalanceItem base_item, CoinBalanceItem quote_item, String symbolPair) {
        this.base_item = base_item;
        this.quote_item = quote_item;
        symbolBase = base_item.getSymbol();
        symbolQuote = quote_item.getSymbol();
        this.symbolPair = symbolPair;
        in_sell_order = false;
        listIndex = -1;
        in_order_buy_sell_cycle = false;
        order_pending = false;
    }

    @Override
    public String toString() {
        String txt;
        txt = base_item.getSymbol() + ": " + NumberFormatter.df6.format(base_item.getFreeValue());
        if (base_item.getLimitValue().compareTo(BigDecimal.ZERO) > 0) {
            txt = txt + " / " + NumberFormatter.df6.format(base_item.getLimitValue());
        }
        if (base_item.isPairKey()) {
            txt = txt + " ["+symbolPair+"]";
        }
        txt = txt + "  ";
        boolean is_changed = base_item.getValue().compareTo(base_item.getInitialValue()) != 0 || in_order_buy_sell_cycle || order_pending || !marker.isEmpty();
        if (is_changed) {
            
            if (base_item.getInitialValue().compareTo(BigDecimal.ZERO) > 0) {
                txt = txt + "[Init: " + NumberFormatter.df6.format(base_item.getInitialValue());
                float percent = 100 * (base_item.getValue().floatValue() - base_item.getInitialValue().floatValue()) / base_item.getInitialValue().floatValue();
                if (Math.abs(percent) > 0.0005) {
                    txt = txt + " " + (percent >= 0 ? "+" : "") + NumberFormatter.df3.format(percent) + "%";
                }
                txt = txt + "]";
            }
            
            /*if (!base_item.isPairKey()) {
                
            } else {
                txt = txt + "pair";
            }*/
            
            /*if (base_item.getOrdersCount() > 0) {
                txt = txt + "; " + base_item.getOrdersCount() + " orders";
            }*/
            
            if (marker.isEmpty()) {
                if (order_pending) {
                    txt = txt + " [PENDING "+(in_sell_order ? "SELL" : "BUY")+"]";
                } else if (in_order_buy_sell_cycle) {
                    txt = txt + " [ORDER]";
                }
            }
            
            if (last_order_price != null && last_order_price.compareTo(BigDecimal.ZERO) > 0) {
                txt = txt + " [Ord: "+NumberFormatter.df8.format(last_order_price)+ " " + symbolQuote + "]";
            }
        }
 
        if (CoinInfoAggregator.getInstance().getLastPrices().containsKey(symbolPair)) {
            txt = txt + " [Cur: "+NumberFormatter.df8.format(CoinInfoAggregator.getInstance().getLastPrices().get(symbolPair))+ " " + symbolQuote;
            
            if (last_order_price != null && last_order_price.compareTo(BigDecimal.ZERO) > 0) {
                double percent = 100 * (CoinInfoAggregator.getInstance().getLastPrices().get(symbolPair) - last_order_price.doubleValue()) / last_order_price.doubleValue();
                if (Math.abs(percent) > 0.0005) {
                    txt = txt + " " + (percent >= 0 ? "+" : "") + NumberFormatter.df3.format(percent) + "%";
                }
            }
            
            txt = txt + "]";
        }
        
        if (!marker.isEmpty()) {
            txt = txt + " ["+marker+"]";
        }
        
        return txt;
    }

    public BigDecimal getValue() {
        return base_item.getValue();
    }
    
    public void setInitialValue(BigDecimal val) {
        base_item.setInitialValue(val);
        runBaseCoinEvent();
    }
    
    private void runBaseCoinEvent() {
        if (base_item.getCoinEvent() != null) base_item.getCoinEvent().onCoinUpdate(base_item);
    }
    private void runQuoteCoinEvent() {
        if (quote_item.getCoinEvent() != null) quote_item.getCoinEvent().onCoinUpdate(quote_item);
    }
    private void runCoinEvents() {
        runBaseCoinEvent();
        runQuoteCoinEvent();
    }
    
    public void preBuyTransaction(BigDecimal summBase, BigDecimal summQuote, boolean change_initial_values) {
        if (!in_order_buy_sell_cycle) {
            last_order_price = BigDecimal.valueOf(CoinInfoAggregator.getInstance().getLastPrices().get(symbolPair));
            if (change_initial_values) {
                base_item.addInitialValue(summBase.multiply(new BigDecimal("-1")));
                quote_item.addInitialValue(summQuote);
            }
            in_order_buy_sell_cycle = true;
            order_pending = false;
            in_sell_order = false;
            summOrderBase = BigDecimal.ZERO;
            summOrderQuote = BigDecimal.ZERO;
            runCoinEvents();
        }
    }

    public void startBuyTransaction(BigDecimal summBase, BigDecimal summQuote) {
        if (!in_order_buy_sell_cycle) {
            last_order_price = BigDecimal.valueOf(CoinInfoAggregator.getInstance().getLastPrices().get(symbolPair));
            summOrderBase = summBase;
            summOrderQuote = summQuote;
            quote_item.addLimitValue(summOrderQuote);
            quote_item.addFreeValue(summOrderQuote.multiply(new BigDecimal("-1")));
            base_item.incActiveOrders();
            quote_item.incActiveOrders();
            in_sell_order = false;
            in_order_buy_sell_cycle = true;
            order_pending = true;
            runCoinEvents();
        }
    }
    public void startSellTransaction(BigDecimal summBase, BigDecimal summQuote) {
        if (in_order_buy_sell_cycle) {
            summOrderBase = summBase;
            summOrderQuote = summQuote;
            base_item.addLimitValue(summOrderBase);
            base_item.addFreeValue(summOrderBase.multiply(new BigDecimal("-1")));
            base_item.incActiveOrders();
            quote_item.incActiveOrders();
            in_sell_order = true;
            order_pending = true;
            runCoinEvents();
        }
    }
    public void confirmTransaction() {
        if (in_sell_order) {
            base_item.addLimitValue(summOrderBase.multiply(new BigDecimal("-1")));
            quote_item.addFreeValue(summOrderQuote);
            in_order_buy_sell_cycle = false;
            last_order_price = BigDecimal.ZERO;
            base_item.incOrderCount();
            quote_item.incOrderCount();
        } else {
            quote_item.addLimitValue(summOrderQuote.multiply(new BigDecimal("-1")));
            base_item.addFreeValue(summOrderBase);
        }
        summOrderBase = BigDecimal.ZERO;
        summOrderQuote = BigDecimal.ZERO;
        in_sell_order = false;
        order_pending = false;
        base_item.decActiveOrders();
        quote_item.decActiveOrders();
        runCoinEvents();
    }

    public void confirmTransactionPart(BigDecimal summPartBase, BigDecimal summPartQuote) {
        if (in_sell_order) {
            base_item.addLimitValue(summPartBase.multiply(new BigDecimal("-1")));
            quote_item.addFreeValue(summPartQuote);
        } else {
            quote_item.addLimitValue(summPartQuote.multiply(new BigDecimal("-1")));
            base_item.addFreeValue(summPartBase);
        }
        summOrderBase = summOrderBase.subtract(summPartBase);
        summOrderQuote = summOrderQuote.subtract(summPartQuote);
        runCoinEvents();
    }

    public void rollbackTransaction() {
        if (in_sell_order) {
            base_item.addLimitValue(summOrderBase.multiply(new BigDecimal("-1")));
            base_item.addFreeValue(summOrderBase);
        } else {
            quote_item.addLimitValue(summOrderQuote.multiply(new BigDecimal("-1")));
            quote_item.addFreeValue(summOrderQuote);
            in_order_buy_sell_cycle = false;
            last_order_price = BigDecimal.ZERO;
        }
        summOrderBase = BigDecimal.ZERO;
        summOrderQuote = BigDecimal.ZERO;
        in_sell_order = false;
        order_pending = false;
        base_item.decActiveOrders();
        quote_item.decActiveOrders();
        runCoinEvents();
    }
    
    public void simpleBuy(BigDecimal summBase, BigDecimal summQuote) {
        last_order_price = BigDecimal.valueOf(CoinInfoAggregator.getInstance().getLastPrices().get(symbolPair));
        summOrderBase = summBase;
        summOrderQuote = summQuote;
        quote_item.addFreeValue(summOrderQuote.multiply(new BigDecimal("-1")));
        base_item.addFreeValue(summOrderBase);
        runCoinEvents();
    }

    public void simpleSell(BigDecimal summBase, BigDecimal summQuote) {
        last_order_price = BigDecimal.valueOf(CoinInfoAggregator.getInstance().getLastPrices().get(symbolPair));
        summOrderBase = summBase;
        summOrderQuote = summQuote;
        quote_item.addFreeValue(summOrderQuote);
        base_item.addFreeValue(summOrderBase.multiply(new BigDecimal("-1")));
        runCoinEvents();
    }
    
    /**
     * @return the base_item
     */
    public CoinBalanceItem getBaseItem() {
        return base_item;
    }

    /**
     * @return the quote_item
     */
    public CoinBalanceItem getQuoteItem() {
        return quote_item;
    }

    /**
     * @return the symbolBase
     */
    public String getSymbolBase() {
        return symbolBase;
    }

    /**
     * @return the symbolPair
     */
    public String getSymbolPair() {
        return symbolPair;
    }

    /**
     * @return the symbolQuote
     */
    public String getSymbolQuote() {
        return symbolQuote;
    }

    /**
     * @return the listIndex
     */
    public int getListIndex() {
        return listIndex;
    }

    /**
     * @param listIndex the listIndex to set
     */
    public void setListIndex(int listIndex) {
        this.listIndex = listIndex;
    }
    
    /**
     * @return the last_order_price
     */
    public BigDecimal getLastOrderPrice() {
        return last_order_price;
    }

    /**
     * @param last_order_price the last_order_price to set
     */
    public void setLastOrderPrice(BigDecimal last_order_price) {
        this.last_order_price = last_order_price;
    }

    /**
     * @return the marker
     */
    public String getMarker() {
        return marker;
    }

    /**
     * @param marker the marker to set
     */
    public void setMarker(String marker) {
        this.marker = marker;
    }

    /**
     * @return the orderEvent
     */
    public PairOrderEvent getOrderEvent() {
        return orderEvent;
    }

    /**
     * @param orderEvent the orderEvent to set
     */
    public void setOrderEvent(PairOrderEvent orderEvent) {
        this.orderEvent = orderEvent;
    }

    /**
     * @return the orderAPIID
     */
    public long getOrderAPIID() {
        return orderAPIID;
    }

    /**
     * @param orderAPIID the orderAPIID to set
     */
    public void setOrderAPIID(long orderAPIID) {
        this.orderAPIID = orderAPIID;
    }

    /**
     * @return the lastCheckHashOrder
     */
    public String getLastCheckHashOrder() {
        return lastCheckHashOrder;
    }

    /**
     * @param lastCheckHashOrder the lastCheckHashOrder to set
     */
    public void setLastCheckHashOrder(String lastCheckHashOrder) {
        this.lastCheckHashOrder = lastCheckHashOrder;
    }

    public void resetCheckHashOrder() {
        this.lastCheckHashOrder = "";
    }

    /**
     * @return the orderProcess
     */
    public ControllableOrderProcess getOrderProcess() {
        return orderProcess;
    }

    /**
     * @param orderProcess the orderProcess to set
     */
    public void setOrderProcess(ControllableOrderProcess orderProcess) {
        this.orderProcess = orderProcess;
    }
}