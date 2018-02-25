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
public class currencyItem {
    private String symbol = "";
    
    private float free_value = 0;
    private float limit_value = 0;
    private float initial_value = -1;
    
    private int orders_count = 0;
    
    private static DecimalFormat df5 = new DecimalFormat("0.#####");
    private static DecimalFormat df6 = new DecimalFormat("0.######");
    
    private boolean pair_key = false;
    private int active_orders = 0;
    
    private int listIndex = -1;
    
    public currencyItem(String symbol) {
        this.symbol = symbol;
        orders_count = 0;
        free_value = 0;
        limit_value = 0;
        initial_value = -1;
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
        txt = getSymbol() + ": " + df6.format(getFreeValue());
        if (getLimitValue() > 0) {
            txt = txt + " / " + df6.format(getLimitValue());
        }
        if (getValue() != getInitialValue() /*|| isInOrder() || isInPendingOrder()*/) {
            txt = txt + " (";
            
            txt = txt + "initially " + df6.format(getInitialValue());
            if (getInitialValue() > 0) {
                float percent = 100 * (getValue() - getInitialValue()) / getInitialValue();
                txt = txt + "; " + (percent >= 0 ? "+" : "") + df5.format(percent) + "%";
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
    
    public void addFreeValue(float _free_value) {
        free_value += _free_value;
        if (free_value < 0) {
            free_value = 0;
        }
    }
    
    public void addLimitValue(float _limit_value) {
        limit_value += _limit_value;
        if (limit_value < 0) {
            limit_value = 0;
        }
    }
    public void addInitialValue(float _initial_value) {
        initial_value += _initial_value;
        if (initial_value < 0) {
            initial_value = 0;
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
    public float getValue() {
        return free_value + limit_value;
    }

    /**
     * @return the free_value
     */
    public float getFreeValue() {
        return free_value;
    }

    /**
     * @param free_value the free_value to set
     */
    public void setFreeValue(float free_value) {
        this.free_value = free_value;
    }

    /**
     * @return the initial_value
     */
    public float getInitialValue() {
        return initial_value;
    }

    /**
     * @param initial_value the initial_value to set
     */
    public void setInitialValue(float initial_value) {
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
    public float getLimitValue() {
        return limit_value;
    }

    /**
     * @param limit_value the limit_value to set
     */
    public void setLimitValue(float limit_value) {
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
