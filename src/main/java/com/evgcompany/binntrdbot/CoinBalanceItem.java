/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import com.evgcompany.binntrdbot.misc.NumberFormatter;
import java.math.BigDecimal;

/**
 *
 * @author EVG_Adminer
 */
public class CoinBalanceItem {
    private String symbol = "";
    
    private BigDecimal free_value = BigDecimal.ZERO;
    private BigDecimal limit_value = BigDecimal.ZERO;
    private BigDecimal initial_value = new BigDecimal("-1");
    
    private int orders_count = 0;
    
    private boolean pair_key = false;
    private int active_orders = 0;
    
    private int listIndex = -1;
    
    public CoinBalanceItem(String symbol) {
        this.symbol = symbol;
        orders_count = 0;
        free_value = BigDecimal.ZERO;
        limit_value = BigDecimal.ZERO;
        initial_value = new BigDecimal("-1");
        pair_key = false;
        listIndex = -1;
    }

    public void incActiveOrders() {
        active_orders++;
    }
    public void decActiveOrders() {
        active_orders--;
    }
    public int getActiveOrders() {
        return active_orders;
    }
    
    @Override
    public String toString() {
        String txt;
        txt = getSymbol() + ": " + NumberFormatter.df6.format(getFreeValue());
        if (limit_value.compareTo(BigDecimal.ZERO) > 0) {
            txt = txt + " / " + NumberFormatter.df6.format(getLimitValue());
        }
        if (getValue().compareTo(initial_value) != 0 /*|| isInOrder() || isInPendingOrder()*/) {
            txt = txt + " (";
            
            txt = txt + "Init: " + NumberFormatter.df6.format(initial_value);
            if (initial_value.compareTo(BigDecimal.ZERO) > 0) {
                float percent = 100 * (getValue().floatValue() - initial_value.floatValue()) / initial_value.floatValue();
                txt = txt + "; " + (percent >= 0 ? "+" : "") + NumberFormatter.df3.format(percent) + "%";
            }
            
            /*if (!isPairKey()) {
                
            } else {
                txt = txt + "pair";
            }*/
            if (getOrdersCount() > 0) {
                txt = txt + "; " + getOrdersCount() + " orders";
            }
            /*if (isInPendingOrder()) {
                txt = txt + "; [PENDING]";
            } else if (isInOrder()) {
                txt = txt + "; [ORDER]";
            }*/
            txt = txt + ")";
        }
        return txt;
    }
    
    public void addFreeValue(BigDecimal _free_value) {
        free_value = free_value.add(_free_value);
        if (free_value.compareTo(BigDecimal.ZERO) == -1) {
            free_value = BigDecimal.ZERO;
        }
    }
    
    public void addLimitValue(BigDecimal _limit_value) {
        limit_value = limit_value.add(_limit_value);
        if (limit_value.compareTo(BigDecimal.ZERO) == -1) {
            limit_value = BigDecimal.ZERO;
        }
    }
    public void addInitialValue(BigDecimal _initial_value) {
        initial_value = initial_value.add(_initial_value);
        if (initial_value.compareTo(BigDecimal.ZERO) == -1) {
            initial_value = BigDecimal.ZERO;
        }
    }

    /**
     * @return the symbol
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * @param symbol the symbol to set
     */
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
    
    /**
     * @return the value
     */
    public BigDecimal getValue() {
        return free_value.add(limit_value);
    }

    /**
     * @return the free_value
     */
    public BigDecimal getFreeValue() {
        return free_value;
    }

    /**
     * @param free_value the free_value to set
     */
    public void setFreeValue(BigDecimal free_value) {
        this.free_value = free_value;
    }

    /**
     * @return the initial_value
     */
    public BigDecimal getInitialValue() {
        return initial_value;
    }

    /**
     * @param initial_value the initial_value to set
     */
    public void setInitialValue(BigDecimal initial_value) {
        this.initial_value = initial_value;
    }

    /**
     * @return the orders_count
     */
    public int getOrdersCount() {
        return orders_count;
    }

    /**
     * inc orders count
     */
    public void incOrderCount() {
        this.orders_count++;
    }

    /**
     * @return the pair_key
     */
    public boolean isPairKey() {
        return pair_key;
    }

    /**
     * @param pair_key the pair_key to set
     */
    public void setPairKey(boolean pair_key) {
        this.pair_key = pair_key;
    }

    /**
     * @return the limit_value
     */
    public BigDecimal getLimitValue() {
        return limit_value;
    }

    /**
     * @param limit_value the limit_value to set
     */
    public void setLimitValue(BigDecimal limit_value) {
        this.limit_value = limit_value;
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
}
