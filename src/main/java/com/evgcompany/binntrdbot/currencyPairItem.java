/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import java.text.DecimalFormat;

/**
 *
 * @author EVG_Adminer
 */
public class currencyPairItem {
    private String symbolBase = "";
    private String symbolPair = "";
    private String symbolQuote = "";
    private currencyItem base_item = null;
    private currencyItem quote_item = null;
    
    private float summOrderBase = 0;
    private float summOrderQuote = 0;
    
    private boolean in_order_buy_sell_cycle;
    private boolean order_pending;
    private boolean in_sell_order = false;
    private float last_order_price = 0;
    private float price = 0;
    
    private int listIndex = -1;
    
    private static DecimalFormat df5 = new DecimalFormat("0.#####");
    private static DecimalFormat df6 = new DecimalFormat("0.######");
    
    public currencyPairItem(currencyItem base_item, currencyItem quote_item, String symbolPair) {
        this.base_item = base_item;
        this.quote_item = quote_item;
        symbolBase = base_item.getSymbol();
        symbolQuote = quote_item.getSymbol();
        this.symbolPair = symbolPair;
        in_sell_order = false;
        listIndex = -1;
        in_order_buy_sell_cycle = false;
        order_pending = false;
        price = 0;
    }

    @Override
    public String toString() {
        String txt;
        txt = base_item.getSymbol() + ": " + df6.format(base_item.getFreeValue());
        if (base_item.getLimitValue() > 0) {
            txt = txt + " / " + df6.format(base_item.getLimitValue());
        }
        if (base_item.isPairKey()) {
            txt = txt + " ["+symbolPair+"]";
        }
        if (base_item.getValue() != base_item.getInitialValue() || in_order_buy_sell_cycle || order_pending) {
            txt = txt + " (";
            
            txt = txt + "initially " + df6.format(base_item.getInitialValue());
            if (base_item.getInitialValue() > 0) {
                float percent = 100 * (base_item.getValue() - base_item.getInitialValue()) / base_item.getInitialValue();
                txt = txt + "; " + (percent >= 0 ? "+" : "") + df5.format(percent) + "%";
            }
            
            /*if (!base_item.isPairKey()) {
                
            } else {
                txt = txt + "pair";
            }*/
            
            if (base_item.getOrdersCount() > 0) {
                txt = txt + "; " + base_item.getOrdersCount() + " orders";
            }
            if (order_pending) {
                txt = txt + "; [PENDING "+(in_sell_order ? "SELL" : "BUY")+"]";
            } else if (in_order_buy_sell_cycle) {
                txt = txt + "; [ORDER]";
            }
            if (!order_pending && last_order_price > 0) {
                float percent = 100 * (price - last_order_price) / last_order_price;
                txt = txt + "; " + (percent >= 0 ? "+" : "") + df5.format(percent) + "% " + symbolQuote;
            }
            txt = txt + ")";
        }
        return txt;
    }

    public float getValue() {
        return base_item.getValue();
    }
    
    public void setInitialValue(float val) {
        base_item.setInitialValue(val);
    }
    
    public void preBuyTransaction(float summBase, float summQuote) {
        if (!in_order_buy_sell_cycle) {
            last_order_price = price;
            base_item.addInitialValue(-summBase);
            quote_item.addInitialValue(summQuote);
            in_order_buy_sell_cycle = true;
            order_pending = false;
            in_sell_order = false;
            summOrderBase = 0;
            summOrderQuote = 0;
        }
    }

    public void startBuyTransaction(float summBase, float summQuote) {
        if (!in_order_buy_sell_cycle) {
            last_order_price = price;
            summOrderBase = summBase;
            summOrderQuote = summQuote;
            quote_item.addLimitValue(summOrderQuote);
            quote_item.addFreeValue(-summOrderQuote);
            base_item.incActiveOrders();
            quote_item.incActiveOrders();
            in_sell_order = false;
            in_order_buy_sell_cycle = true;
            order_pending = true;
        }
    }
    public void startSellTransaction(float summBase, float summQuote) {
        if (in_order_buy_sell_cycle) {
            summOrderBase = summBase;
            summOrderQuote = summQuote;
            base_item.addLimitValue(summOrderBase);
            base_item.addFreeValue(-summOrderBase);
            base_item.incActiveOrders();
            quote_item.incActiveOrders();
            in_sell_order = true;
            order_pending = true;
        }
    }
    public void confirmTransaction() {
        if (in_sell_order) {
            base_item.addLimitValue(-summOrderBase);
            quote_item.addFreeValue(summOrderQuote);
            in_order_buy_sell_cycle = false;
            last_order_price = 0;
        } else {
            quote_item.addLimitValue(-summOrderQuote);
            base_item.addFreeValue(summOrderBase);
        }
        summOrderBase = 0;
        summOrderQuote = 0;
        in_sell_order = false;
        order_pending = false;
        base_item.decActiveOrders();
        quote_item.decActiveOrders();
    }
    
    public void rollbackTransaction() {
        if (in_sell_order) {
            base_item.addLimitValue(-summOrderBase);
            base_item.addFreeValue(summOrderBase);
        } else {
            quote_item.addLimitValue(-summOrderQuote);
            quote_item.addFreeValue(summOrderQuote);
            in_order_buy_sell_cycle = false;
            last_order_price = 0;
        }
        summOrderBase = 0;
        summOrderQuote = 0;
        in_sell_order = false;
        order_pending = false;
        base_item.decActiveOrders();
        quote_item.decActiveOrders();
    }
    
    /**
     * @return the base_item
     */
    public currencyItem getBaseItem() {
        return base_item;
    }

    /**
     * @return the quote_item
     */
    public currencyItem getQuoteItem() {
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
     * @param price the price to set
     */
    public void setPrice(float price) {
        this.price = price;
    }
    
    /**
     * @return the price
     */
    float getPrice() {
        return price;
    }

    /**
     * @return the last_order_price
     */
    public float getLastOrderPrice() {
        return last_order_price;
    }

    /**
     * @param last_order_price the last_order_price to set
     */
    public void setLastOrderPrice(float last_order_price) {
        this.last_order_price = last_order_price;
    }
}
