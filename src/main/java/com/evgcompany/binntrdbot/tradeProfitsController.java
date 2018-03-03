/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.Account;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.account.NewOrderResponse;
import java.text.DecimalFormat;
import java.util.List;
import java.util.concurrent.Semaphore;
import javax.swing.DefaultListModel;

import static com.binance.api.client.domain.account.NewOrder.limitBuy;
import static com.binance.api.client.domain.account.NewOrder.limitSell;
import static com.binance.api.client.domain.account.NewOrder.marketBuy;
import static com.binance.api.client.domain.account.NewOrder.marketSell;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.Trade;
import com.binance.api.client.domain.account.request.CancelOrderRequest;
import com.binance.api.client.domain.account.request.OrderStatusRequest;
import com.binance.api.client.domain.market.TickerStatistics;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author EVG_Adminer
 */
public class tradeProfitsController {
    
    private mainApplication app;
    private static final Semaphore SEMAPHORE = new Semaphore(1, true);
    private static final Semaphore SEMAPHORE_ADDCOIN = new Semaphore(1, true);

    private Map<String, currencyItem> curr_map = new HashMap<String, currencyItem>();
    private Map<String, currencyPairItem> pair_map = new HashMap<String, currencyPairItem>();

    private DefaultListModel<String> listProfitModel = new DefaultListModel<String>();
    private DefaultListModel<String> listCurrenciesModel = new DefaultListModel<String>();

    private boolean isTestMode = false;
    private boolean isLimitedOrders = false;

    private BigDecimal tradeComissionPercent = new BigDecimal("0.05");
    private String tradeComissionCurrency = "BNB";

    private BinanceApiRestClient client = null;
    private Account account = null;

    private static DecimalFormat df5 = new DecimalFormat("0.#####");
    private static DecimalFormat df6 = new DecimalFormat("0.######");

    public tradeProfitsController(mainApplication _app) {
        app = _app;
    }
    
    public currencyItem getProfitData(String symbolAsset) {
        return curr_map.get(symbolAsset);
    }
    
    public void showTradeComissionCurrency() {
        if (tradeComissionCurrency.isEmpty() || client == null) {
            return;
        }
        currencyItem cbase = curr_map.get(tradeComissionCurrency);
        if (cbase == null) {
            cbase = addCurrItem(tradeComissionCurrency);
            cbase.setListIndex(listProfitModel.size());
            curr_map.put(tradeComissionCurrency, cbase);
            updateAllBalances(false);
            listProfitModel.addElement(cbase.toString());
        }
    }
    
    private BigDecimal getLastTradePrice(String symbolPair, boolean isBuyer) {
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
    
    public boolean canBuy(String symbolPair, BigDecimal baseAmount, BigDecimal price) {
        currencyPairItem pair = pair_map.get(symbolPair);
        return (pair != null) && (pair.getQuoteItem().getFreeValue().compareTo(baseAmount.multiply(price)) >= 0);
    }
    public boolean canSell(String symbolPair, BigDecimal baseAmount) {
        currencyPairItem pair = pair_map.get(symbolPair);
        return (pair != null) && (pair.getBaseItem().getFreeValue().compareTo(baseAmount) >= 0);
    }

    private void testPayTradeComission(BigDecimal price, String quoteSymbol) {
        boolean use_spec_currency = !tradeComissionCurrency.isEmpty() && !tradeComissionCurrency.equals(quoteSymbol);
        BigDecimal comission_quote = price
                .multiply(
                    BigDecimal.valueOf(100).subtract(tradeComissionPercent)
                )
                .divide(BigDecimal.valueOf(100));
        if (use_spec_currency) {
            String comission_pair = (tradeComissionCurrency + quoteSymbol).toUpperCase();
            BigDecimal pair_price = null;
            if (pair_map.containsKey(comission_pair)) {
                pair_price = pair_map.get(comission_pair).getPrice();
            } else {
                TickerStatistics tc = client.get24HrPriceStatistics(comission_pair);
                if (tc != null && !tc.getLastPrice().isEmpty()) {
                    pair_price = new BigDecimal(tc.getLastPrice());
                    placeOrUpdatePair(tradeComissionCurrency, quoteSymbol, comission_pair, true);
                }
            }
            if (pair_price != null && pair_price.compareTo(BigDecimal.ZERO) > 0) {
                currencyItem ccomm = curr_map.get(tradeComissionCurrency);
                ccomm.addFreeValue(comission_quote.multiply(pair_price).multiply(BigDecimal.valueOf(-1)));
            } else {
                use_spec_currency = false;
            }
        }
        if (!use_spec_currency) {
            currencyItem cquote = curr_map.get(quoteSymbol);
            cquote.addFreeValue(comission_quote.multiply(BigDecimal.valueOf(-1)));
        }
    }
    
    public long Buy(String symbolPair, BigDecimal baseAmount, BigDecimal price, boolean is_preordered) {
        long result = 0;
        try {
            SEMAPHORE.acquire();
            currencyPairItem pair = pair_map.get(symbolPair);
            if (pair == null) {
                app.log("Pair " + symbolPair + " not found!");
                SEMAPHORE.release();
                return -1;
            }
            if (!is_preordered) {
                if (pair.getQuoteItem().getFreeValue().compareTo(baseAmount.multiply(price)) < 0) {
                    app.log("Not enough " + pair.getSymbolQuote() + " to buy " + df6.format(baseAmount) + " " + pair.getSymbolBase() + ". Need " + df6.format(baseAmount.multiply(price)) + " but have " + df6.format(pair.getQuoteItem().getFreeValue()), true, true);
                    SEMAPHORE.release();
                    return -1;
                }
                if (!isTestMode) {
                    NewOrderResponse newOrderResponse;
                    if (isLimitedOrders) {
                        //price = price / (1 + 0.01f * tradeComissionPercent);
                        newOrderResponse = client.newOrder(limitBuy(symbolPair, TimeInForce.GTC, df5.format(baseAmount).replace(".","").replace(",","."), df5.format(price).replace(".","").replace(",",".")));
                        result = newOrderResponse.getOrderId();
                    } else {
                        newOrderResponse = client.newOrder(marketBuy(symbolPair, df5.format(baseAmount).replace(".","").replace(",",".")));
                        result = newOrderResponse.getOrderId();
                    }
                    if (newOrderResponse != null && newOrderResponse.getOrderId() > 0) {
                        pair.startBuyTransaction(baseAmount, baseAmount.multiply(price));
                        app.log("Order id = " + newOrderResponse.getOrderId(), false, true);
                        Thread.sleep(550);
                        Order mord = client.getOrderStatus(new OrderStatusRequest(newOrderResponse.getSymbol(), newOrderResponse.getOrderId()));
                        if (mord.getStatus() != OrderStatus.NEW && mord.getStatus() != OrderStatus.PARTIALLY_FILLED && mord.getStatus() != OrderStatus.FILLED) {
                            pair.rollbackTransaction();
                            app.log("Order cancelled!", true, true);
                            SEMAPHORE.release();
                            return -1;
                        } else if (mord.getStatus() == OrderStatus.FILLED) {
                            pair.confirmTransaction();
                            BigDecimal lastTradePrice = getLastTradePrice(symbolPair, true);
                            if (lastTradePrice.compareTo(BigDecimal.ZERO) > 0) {
                                app.log("Real order market price = " + df6.format(lastTradePrice), true, true);
                                price = lastTradePrice;
                                pair.setPrice(price);
                                pair.setLastOrderPrice(lastTradePrice);
                            } else {
                                pair.setPrice(price);
                                pair.setLastOrderPrice(price);
                            }
                            result = 0;
                        }
                    } else {
                        app.log("Order error!", true, true);
                        SEMAPHORE.release();
                        return -1;
                    }
                } else {
                    pair.setPrice(price);
                    pair.setLastOrderPrice(price);
                    pair.startBuyTransaction(baseAmount, baseAmount.multiply(price));
                    pair.confirmTransaction();
                    testPayTradeComission(baseAmount.multiply(price), pair.getSymbolQuote());
                }
            } else {
                pair.setPrice(price);
                pair.preBuyTransaction(baseAmount, baseAmount.multiply(price));
            }
            updatePairText(symbolPair, !isTestMode);
            SEMAPHORE.release();
        } catch (Exception e) {
            app.log("Error: " + e.getMessage(), true, true);
            SEMAPHORE.release();
            return -1;
        }
        return result;
    }

    public long Sell(String symbolPair, BigDecimal baseAmount, BigDecimal price, List<Long> OrderToCancel) {
        long result = 0;
        try {
            SEMAPHORE.acquire();
            currencyPairItem pair = pair_map.get(symbolPair);
            if (pair == null) {
                app.log("Pair " + symbolPair + " not found!");
                SEMAPHORE.release();
                return -1;
            }
            if (pair.getBaseItem().getFreeValue().compareTo(baseAmount) < 0) {
                app.log("Not enough " + pair.getSymbolBase() + " to sell (" + df6.format(pair.getBaseItem().getFreeValue()) + " < " + df6.format(baseAmount) + ")", true, true);
                SEMAPHORE.release();
                return -1;
            }
            if (!isTestMode) {
                if (!OrderToCancel.isEmpty()) {
                    OrderToCancel.forEach((order_id)->{
                        client.cancelOrder(new CancelOrderRequest(symbolPair, order_id));
                        app.log("Canceling LimitOrder "+order_id+"!", true, true);
                    });
                    Thread.sleep(750);
                }
                NewOrderResponse newOrderResponse;
                if (isLimitedOrders) {
                    //price = price * (1 + 0.01f * tradeComissionPercent);
                    newOrderResponse = client.newOrder(limitSell(symbolPair, TimeInForce.GTC, df5.format(baseAmount).replace(".","").replace(",","."), df5.format(price).replace(".","").replace(",",".")));
                    result = newOrderResponse.getOrderId();
                } else {
                    newOrderResponse = client.newOrder(marketSell(symbolPair, df5.format(baseAmount).replace(".","").replace(",",".")));
                    result = newOrderResponse.getOrderId();
                }
                if (newOrderResponse != null && newOrderResponse.getOrderId() > 0) {
                    pair.startSellTransaction(baseAmount, baseAmount.multiply(price));
                    app.log("Order id = " + newOrderResponse.getOrderId(), false, true);
                    Thread.sleep(550);
                    Order mord = client.getOrderStatus(new OrderStatusRequest(newOrderResponse.getSymbol(), newOrderResponse.getOrderId()));
                    if (mord.getStatus() != OrderStatus.NEW && mord.getStatus() != OrderStatus.PARTIALLY_FILLED && mord.getStatus() != OrderStatus.FILLED) {
                        pair.rollbackTransaction();
                        app.log("Order cancelled!", true, true);
                        SEMAPHORE.release();
                        return -1;
                    } else if (mord.getStatus() == OrderStatus.FILLED) {
                        pair.confirmTransaction();
                        BigDecimal lastTradePrice = getLastTradePrice(symbolPair, false);
                        if (lastTradePrice.compareTo(BigDecimal.ZERO) > 0) {
                            app.log("Real order market price = " + df6.format(lastTradePrice), true, true);
                            price = lastTradePrice;
                            pair.setPrice(price);
                            pair.setLastOrderPrice(lastTradePrice);
                        } else {
                            pair.setPrice(price);
                            pair.setLastOrderPrice(price);
                        }
                        result = 0;
                    }
                } else {
                    app.log("Order error!", true, true);
                    SEMAPHORE.release();
                    return -1;
                }
            } else {
                pair.setPrice(price);
                pair.setLastOrderPrice(price);
                pair.startSellTransaction(baseAmount, baseAmount.multiply(price));
                pair.confirmTransaction();
                testPayTradeComission(baseAmount.multiply(price), pair.getSymbolQuote());
            }
            updatePairText(symbolPair, !isTestMode);
            SEMAPHORE.release();
        } catch (Exception e) {
            app.log("Error: " + e.getMessage(), true, true);
            SEMAPHORE.release();
            return -1;
        }
        return result;
    }

    public void setPairPrice(String symbolPair, BigDecimal price) {
        currencyPairItem pair = pair_map.get(symbolPair);
        if (pair != null) {
            pair.setPrice(price);
            updatePairText(symbolPair, false);
        }
    }

    void finishOrder(String symbolPair, boolean isok, BigDecimal sold_price) {
        currencyPairItem pair = pair_map.get(symbolPair);
        if (pair != null) {
            if (isok) {
                pair.confirmTransaction();
            } else {
                pair.rollbackTransaction();
            }
        }
    }
    
    public void updateAllBalances(boolean updateLists) {
        if (client != null) {
            account = client.getAccount();
            List<AssetBalance> allBalances = account.getBalances();
            for (int i = 0; i < allBalances.size(); i++) {
                String symbol = allBalances.get(i).getAsset().toUpperCase();
                if (curr_map.containsKey(symbol)) {
                    currencyItem curr = curr_map.get(symbol);
                    if (!isTestMode || curr.getInitialValue().compareTo(BigDecimal.ZERO) < 0) {
                        BigDecimal balance_free = new BigDecimal(allBalances.get(i).getFree());
                        BigDecimal balance_limit = new BigDecimal(allBalances.get(i).getLocked());
                        BigDecimal balance = balance_free.add(balance_limit);
                        curr.setFreeValue(balance_free);
                        curr.setLimitValue(balance_limit);
                        if (curr.getInitialValue().compareTo(BigDecimal.ZERO) < 0) {
                            curr.setInitialValue(curr.getValue());
                        }
                    }
                }
            }
            if (updateLists) {
                for (Map.Entry<String, currencyPairItem> entry : pair_map.entrySet()) {
                    currencyPairItem curr = entry.getValue();
                    if (curr != null) {
                        if (curr.getListIndex() >= 0) {
                            listCurrenciesModel.set(curr.getListIndex(), curr.toString());
                        }
                        if (curr.getQuoteItem().getListIndex() >= 0) {
                            listProfitModel.set(curr.getQuoteItem().getListIndex(), curr.getQuoteItem().toString());
                        }
                    }
                }
            }
        }
    }

    public void updateAllBalances() {
        updateAllBalances(true);
    }
    
    private currencyItem addCurrItem(String symbol) {
        currencyItem result = new currencyItem(symbol);
        return result;
    }

    public void updateBaseSymbolText(String symbolBase, boolean updateBalance) {
        if (updateBalance) {
            updateAllBalances();
        }
        for (Map.Entry<String, currencyPairItem> entry : pair_map.entrySet()) {
            currencyPairItem curr = entry.getValue();
            if (curr != null && curr.getSymbolBase() == symbolBase) {
                listCurrenciesModel.set(curr.getListIndex(), curr.toString());
                listProfitModel.set(curr.getQuoteItem().getListIndex(), curr.toString());
            }
        }
    }
    public void updatePairText(String symbolPair, boolean updateBalance) {
        placeOrUpdatePair("", "", symbolPair, updateBalance);
    }

    public void placeOrUpdatePair(String symbolBase, String symbolQuote, String symbolPair, boolean updateBalance) {
        try {
            SEMAPHORE_ADDCOIN.acquire();
            symbolBase = symbolBase.toUpperCase();
            symbolQuote = symbolQuote.toUpperCase();
            symbolPair = symbolPair.toUpperCase();
            currencyPairItem cpair = pair_map.get(symbolPair);
            if (cpair == null) {
                if (!symbolBase.isEmpty() && !symbolQuote.isEmpty()) {
                    currencyItem cbase = curr_map.get(symbolBase);
                    currencyItem cquote = curr_map.get(symbolQuote);
                    if (cbase == null) {
                        cbase = addCurrItem(symbolBase);
                        cbase.setListIndex(-1);
                        curr_map.put(symbolBase, cbase);
                    } else {
                        cbase.setPairKey(true);
                    }
                    if (cquote == null) {
                        cquote = addCurrItem(symbolQuote);
                        cquote.setListIndex(listProfitModel.size());
                        curr_map.put(symbolQuote, cquote);
                    }
                    cpair = new currencyPairItem(cbase, cquote, symbolPair);
                    cpair.setListIndex(listCurrenciesModel.size());
                    pair_map.put(symbolPair, cpair);
                    if (updateBalance) {
                        updateAllBalances(false);
                    }
                    listCurrenciesModel.addElement(cpair.toString());
                    if (cquote.getListIndex() < listProfitModel.size()) {
                        listProfitModel.set(cquote.getListIndex(), cquote.toString());
                    } else {
                        listProfitModel.addElement(cquote.toString());
                    }
                }
            } else {
                if (updateBalance) {
                    updateAllBalances();
                }
                if (cpair.getListIndex() >= 0) {
                    listCurrenciesModel.set(cpair.getListIndex(), cpair.toString());
                }
                if (cpair.getBaseItem().getListIndex() >= 0) {
                    listProfitModel.set(cpair.getBaseItem().getListIndex(), cpair.getBaseItem().toString());
                }
                if (cpair.getQuoteItem().getListIndex() >= 0) {
                    listProfitModel.set(cpair.getQuoteItem().getListIndex(), cpair.getQuoteItem().toString());
                }
            }
        } catch (Exception e) {
        }
        SEMAPHORE_ADDCOIN.release();
    }

    /**
     * @return the listProfitModel
     */
    public DefaultListModel<String> getListProfitModel() {
        return listProfitModel;
    }

    /**
     * @return the listCurrenciesModel
     */
    public DefaultListModel<String> getListCurrenciesModel() {
        return listCurrenciesModel;
    }

    /**
     * @return the account
     */
    public Account getAccount() {
        return account;
    }

    public void setClient(BinanceApiRestClient _client) {
        client = _client;
        account = client.getAccount();
    }

    public String getNthCurrencyPair(int n) {
        for (Map.Entry<String, currencyPairItem> entry : pair_map.entrySet()) {
            if (entry.getValue().getListIndex() == n) {
                return entry.getValue().getSymbolPair();
            }
        }
        return null;
    }
    
    /**
     * @return the isTestMode
     */
    public boolean isTestMode() {
        return isTestMode;
    }

    /**
     * @param isTestMode the isTestMode to set
     */
    public void setTestMode(boolean isTestMode) {
        this.isTestMode = isTestMode;
    }

    /**
     * @return the tradeComissionPercent
     */
    public BigDecimal getTradeComissionPercent() {
        return tradeComissionPercent;
    }

    /**
     * @param tradeComissionPercent the tradeComissionPercent to set
     */
    public void setTradeComissionPercent(BigDecimal tradeComissionPercent) {
        this.tradeComissionPercent = tradeComissionPercent;
    }

    void removeCurrencyPair(String symbolPair) {
        if (pair_map.containsKey(symbolPair)) {
            int list_index_to_remove = pair_map.get(symbolPair).getListIndex();
            listCurrenciesModel.remove(list_index_to_remove);
            pair_map.remove(symbolPair);
            for (Map.Entry<String, currencyPairItem> entry : pair_map.entrySet()) {
                if (entry.getValue().getListIndex() > list_index_to_remove) {
                    entry.getValue().setListIndex(entry.getValue().getListIndex() - 1);
                }
            }
        }
    }

    /**
     * @return the isLimitedOrders
     */
    public boolean isLimitedOrders() {
        return isLimitedOrders;
    }

    /**
     * @param isLimitedOrders the isLimitedOrders to set
     */
    public void setLimitedOrders(boolean isLimitedOrders) {
        this.isLimitedOrders = isLimitedOrders;
    }
}
