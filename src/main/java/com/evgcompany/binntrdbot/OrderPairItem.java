/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import com.evgcompany.binntrdbot.coinrating.CoinInfoAggregator;
import com.evgcompany.binntrdbot.events.PairOrderCheckEvent;
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
    
    private BigDecimal transactionOrderBase = BigDecimal.ZERO;
    private BigDecimal transactionOrderQuote = BigDecimal.ZERO;
    
    private boolean inTransaction = false;
    private int pyramidSize = 0;
    private boolean lastTransactionSell = false;

    private BigDecimal lastOrderPrice = BigDecimal.ZERO;

    private String marker = "";
    
    private int listIndex = -1;
    private PairOrderEvent orderEvent = null;
    private PairOrderCheckEvent orderCancelCheckEvent = null;
    private ControllableOrderProcess orderProcess = null;
    
    private long orderAPIID = 0;
    
    private String lastCheckHashOrder = "";
    
    public OrderPairItem(CoinBalanceItem base_item, CoinBalanceItem quote_item, String symbolPair) {
        this.base_item = base_item;
        this.quote_item = quote_item;
        symbolBase = base_item.getSymbol();
        symbolQuote = quote_item.getSymbol();
        this.symbolPair = symbolPair;
        lastTransactionSell = false;
        listIndex = -1;
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
        boolean is_changed = base_item.getValue().compareTo(base_item.getInitialValue()) != 0 || pyramidSize != 0 || inTransaction || !marker.isEmpty();
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
                if (inTransaction) {
                    txt = txt + " [PENDING "+(lastTransactionSell ? "SELL" : "BUY");
                    if (pyramidSize < 0 && lastTransactionSell || pyramidSize > 0 && !lastTransactionSell) {
                        txt = txt + " " + (Math.abs(pyramidSize) + 1);
                    }
                    txt = txt + "]";
                } else if (pyramidSize < 0) {
                    txt = txt + " [ORDER SHORT "+(-pyramidSize)+"]";
                } else if (pyramidSize > 0) {
                    txt = txt + " [ORDER LONG "+(pyramidSize)+"]";
                }
            }
        }
 
        if (lastOrderPrice != null && lastOrderPrice.compareTo(BigDecimal.ZERO) > 0) {
            txt = txt + " [Ord: "+NumberFormatter.df8.format(lastOrderPrice)+ " " + symbolQuote + "]";
        }
        
        if (CoinInfoAggregator.getInstance().getLastPrices().containsKey(symbolPair)) {
            txt = txt + " [Cur: "+NumberFormatter.df8.format(CoinInfoAggregator.getInstance().getLastPrices().get(symbolPair))+ " " + symbolQuote;
            
            if (lastOrderPrice != null && lastOrderPrice.compareTo(BigDecimal.ZERO) > 0) {
                double percent = 100 * (CoinInfoAggregator.getInstance().getLastPrices().get(symbolPair) - lastOrderPrice.doubleValue()) / lastOrderPrice.doubleValue();
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
    
    public void preBuyTransaction(BigDecimal summBase, BigDecimal summQuote, BigDecimal transactionPrice, boolean change_initial_values) {
        System.out.println("preBuyTransaction");
        if (!inTransaction) {
            lastOrderPrice = transactionPrice;
            if (change_initial_values) {
                base_item.addInitialValue(summBase.multiply(new BigDecimal("-1")));
                quote_item.addInitialValue(summQuote);
            }
            pyramidSize++;
            lastTransactionSell = false;
            transactionOrderBase = BigDecimal.ZERO;
            transactionOrderQuote = BigDecimal.ZERO;
            runCoinEvents();
        } else {
            System.out.println("Trying to preBuyTransaction but not in transaction...");
            try { new Exception().printStackTrace();} catch(Exception e) {}
        }
    }

    public void startBuyTransaction(BigDecimal summBase, BigDecimal summQuote, BigDecimal transactionPrice) {
        System.out.println("startBuyTransaction");
        if (!inTransaction) {
            inTransaction = true;
            lastOrderPrice = transactionPrice;
            transactionOrderBase = summBase;
            transactionOrderQuote = summQuote;
            quote_item.addLimitValue(transactionOrderQuote);
            quote_item.addFreeValue(transactionOrderQuote.multiply(new BigDecimal("-1")));
            base_item.incActiveOrders();
            quote_item.incActiveOrders();
            lastTransactionSell = false;
            runCoinEvents();
        } else {
            System.out.println("Trying to startBuyTransaction but already in transaction...");
            try { new Exception().printStackTrace();} catch(Exception e) {}
        }
    }
    public void startSellTransaction(BigDecimal summBase, BigDecimal summQuote, BigDecimal transactionPrice) {
        System.out.println("startSellTransaction");
        if (!inTransaction) {
            inTransaction = true;
            lastOrderPrice = transactionPrice;
            transactionOrderBase = summBase;
            transactionOrderQuote = summQuote;
            base_item.addLimitValue(transactionOrderBase);
            base_item.addFreeValue(transactionOrderBase.multiply(new BigDecimal("-1")));
            base_item.incActiveOrders();
            quote_item.incActiveOrders();
            lastTransactionSell = true;
            runCoinEvents();
        } else {
            System.out.println("Trying to startSellTransaction but already in transaction...");
            try { new Exception().printStackTrace();} catch(Exception e) {}
        }
    }
    public void confirmTransaction() {
        System.out.println("confirmTransaction");
        if (inTransaction) {
            if (lastTransactionSell) {
                pyramidSize--;
                base_item.addLimitValue(transactionOrderBase.multiply(new BigDecimal("-1")));
                quote_item.addFreeValue(transactionOrderQuote);
            } else {
                pyramidSize++;
                quote_item.addLimitValue(transactionOrderQuote.multiply(new BigDecimal("-1")));
                base_item.addFreeValue(transactionOrderBase);
            }
            base_item.incOrderCount();
            quote_item.incOrderCount();
            base_item.decActiveOrders();
            quote_item.decActiveOrders();
            transactionOrderBase = BigDecimal.ZERO;
            transactionOrderQuote = BigDecimal.ZERO;
            inTransaction = false;
            lastTransactionSell = false;
            runCoinEvents();
        } else {
            System.out.println("Trying to confirmTransaction but not in transaction...");
            try { new Exception().printStackTrace();} catch(Exception e) {}
        }
    }

    public void confirmTransactionPart(BigDecimal summPartBase, BigDecimal summPartQuote) {
        System.out.println("confirmTransactionPart");
        if (inTransaction) {
            if (lastTransactionSell) {
                pyramidSize--;
                base_item.addLimitValue(summPartBase.multiply(new BigDecimal("-1")));
                quote_item.addFreeValue(summPartQuote);
            } else {
                pyramidSize++;
                quote_item.addLimitValue(summPartQuote.multiply(new BigDecimal("-1")));
                base_item.addFreeValue(summPartBase);
            }
            transactionOrderBase = transactionOrderBase.subtract(summPartBase);
            transactionOrderQuote = transactionOrderQuote.subtract(summPartQuote);
            runCoinEvents();
        } else {
            System.out.println("Trying to confirmTransactionPart but not in transaction...");
            try { new Exception().printStackTrace();} catch(Exception e) {}
        }
    }

    public void rollbackTransaction() {
        System.out.println("rollbackTransaction");
        if (inTransaction) {
            if (lastTransactionSell) {
                base_item.addLimitValue(transactionOrderBase.multiply(new BigDecimal("-1")));
                base_item.addFreeValue(transactionOrderBase);
            } else {
                quote_item.addLimitValue(transactionOrderQuote.multiply(new BigDecimal("-1")));
                quote_item.addFreeValue(transactionOrderQuote);
            }
            transactionOrderBase = BigDecimal.ZERO;
            transactionOrderQuote = BigDecimal.ZERO;
            inTransaction = false;
            lastTransactionSell = false;
            base_item.decActiveOrders();
            quote_item.decActiveOrders();
            runCoinEvents();
        } else {
            System.out.println("Trying to rollbackTransaction but not in transaction...");
            try { new Exception().printStackTrace();} catch(Exception e) {}
        }
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
        return lastOrderPrice;
    }

    /**
     * @param last_order_price the last_order_price to set
     */
    public void setLastOrderPrice(BigDecimal last_order_price) {
        this.lastOrderPrice = last_order_price;
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

    /**
     * @return the orderCancelCheckEvent
     */
    public PairOrderCheckEvent getOrderCancelCheckEvent() {
        return orderCancelCheckEvent;
    }

    /**
     * @param orderCancelCheckEvent the orderCancelCheckEvent to set
     */
    public void setOrderCancelCheckEvent(PairOrderCheckEvent orderCancelCheckEvent) {
        this.orderCancelCheckEvent = orderCancelCheckEvent;
    }

    /**
     * @return the pyramidSize
     */
    public int getPyramidSize() {
        return pyramidSize;
    }
}
