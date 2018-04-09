/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.account.AssetBalance;
import com.evgcompany.binntrdbot.api.TradingAPIAbstractInterface;
import java.text.DecimalFormat;
import java.util.List;
import java.util.concurrent.Semaphore;
import javax.swing.DefaultListModel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author EVG_Adminer
 */
public class tradeProfitsController {

    public enum LimitedOrderMode {
        LOMODE_SELL, LOMODE_BUY, LOMODE_SELLANDBUY
    }
    
    private final mainApplication app;
    private static final Semaphore SEMAPHORE = new Semaphore(1, true);
    private static final Semaphore SEMAPHORE_ADDCOIN = new Semaphore(1, true);

    private final Map<String, currencyItem> curr_map = new HashMap<>();
    private final Map<String, currencyPairItem> pair_map = new HashMap<>();

    private final DefaultListModel<String> listProfitModel = new DefaultListModel<>();
    private final DefaultListModel<String> listCurrenciesModel = new DefaultListModel<>();

    private boolean isTestMode = false;
    private boolean isLimitedOrders = false;
    private LimitedOrderMode limitedOrderMode = LimitedOrderMode.LOMODE_SELLANDBUY;

    private BigDecimal tradeComissionPercent = new BigDecimal("0.05");
    private BigDecimal tradeMinProfitPercent;
    private final String tradeComissionCurrency = "BNB";
    
    private BigDecimal stopLossPercent = null;
    private BigDecimal stopGainPercent = null;
    private boolean lowHold = true;
    
    private List<String> autoStrategies = new ArrayList<>();
    private boolean autoWalkForward = false;
    private boolean autoPickStrategyParams = false;

    private TradingAPIAbstractInterface client = null;

    private static final DecimalFormat df5 = new DecimalFormat("0.#####");
    private static final DecimalFormat df6 = new DecimalFormat("0.######");

    public tradeProfitsController(mainApplication _app) {
        app = _app;
        tradeMinProfitPercent = tradeComissionPercent.multiply(BigDecimal.valueOf(3));
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
    
    public BigDecimal getOrderAssetAmount(String symbolAsset, BigDecimal percent) {
        currencyItem quote = curr_map.get(symbolAsset);
        if (quote != null) {
            BigDecimal quoteBalance;
            if (quote.getInitialValue().compareTo(quote.getValue()) >= 0) {
                quoteBalance = quote.getInitialValue();
            } else {
                quoteBalance = quote.getValue();
            }
            quoteBalance = quoteBalance.multiply(percent.divide(BigDecimal.valueOf(100)));
            if (quote.getFreeValue().compareTo(quoteBalance) < 0) {
                quoteBalance = quote.getFreeValue();
            }
            return quoteBalance;
        }
        return BigDecimal.ZERO;
    }
    
    public boolean canBuy(String symbolPair, BigDecimal baseAmount, BigDecimal price) {
        currencyPairItem pair = pair_map.get(symbolPair);
        return (pair != null) && baseAmount.compareTo(BigDecimal.ZERO) > 0 && price.compareTo(BigDecimal.ZERO) > 0 && (pair.getQuoteItem().getFreeValue().compareTo(baseAmount.multiply(price)) >= 0);
    }
    public boolean canSell(String symbolPair, BigDecimal baseAmount) {
        currencyPairItem pair = pair_map.get(symbolPair);
        return (pair != null) && baseAmount.compareTo(BigDecimal.ZERO) > 0 && (pair.getBaseItem().getFreeValue().compareTo(baseAmount) >= 0);
    }

    private void testPayTradeComission(BigDecimal price, String quoteSymbol) {
        boolean use_spec_currency = !tradeComissionCurrency.isEmpty() && !tradeComissionCurrency.equals(quoteSymbol);
        BigDecimal comission_quote = price.multiply(tradeComissionPercent).divide(BigDecimal.valueOf(100));
        if (use_spec_currency) {
            String comission_pair = (tradeComissionCurrency + quoteSymbol).toUpperCase();
            BigDecimal pair_price = null;
            if (pair_map.containsKey(comission_pair)) {
                pair_price = pair_map.get(comission_pair).getPrice();
            } else {
                pair_price = client.getCurrentPrice(comission_pair);
            }
            if (pair_price != null && pair_price.compareTo(BigDecimal.ZERO) > 0) {
                currencyItem ccomm = curr_map.get(tradeComissionCurrency);
                BigDecimal trade_com = comission_quote.divide(pair_price, RoundingMode.HALF_DOWN);
                if (ccomm != null && ccomm.getFreeValue().compareTo(trade_com) > 0) {
                    ccomm.addFreeValue(trade_com.multiply(BigDecimal.valueOf(-1)));
                    updateBaseSymbolText(tradeComissionCurrency, false);
                } else {
                    use_spec_currency = false;
                }
            } else {
                use_spec_currency = false;
            }
        }
        if (!use_spec_currency) {
            currencyItem cquote = curr_map.get(quoteSymbol);
            cquote.addFreeValue(comission_quote.multiply(BigDecimal.valueOf(-1)));
        }
    }
    
    public long PreBuySell(String symbolPair, BigDecimal baseAmount, BigDecimal buyPrice, BigDecimal sellAmount, BigDecimal sellPrice) {
        long result = 0;
        currencyPairItem pair = pair_map.get(symbolPair);
        pair.setPrice(buyPrice);
        pair.preBuyTransaction(baseAmount, baseAmount.multiply(buyPrice));
        if (sellAmount != null && sellAmount.compareTo(BigDecimal.ZERO) > 0) {
            pair.startSellTransaction(sellAmount, sellAmount.multiply(sellPrice));
            if (isTestMode) {
                result = 1;
            }
        }
        updatePairText(symbolPair, !isTestMode);
        return result;
    }
    
    public long Buy(String symbolPair, BigDecimal baseAmount, BigDecimal price) {
        long result = 0;
        try {
            SEMAPHORE.acquire();
            currencyPairItem pair = pair_map.get(symbolPair);
            if (pair == null) {
                app.log("Pair " + symbolPair + " not found!");
                SEMAPHORE.release();
                return -1;
            }
            if (pair.getQuoteItem().getFreeValue().compareTo(baseAmount.multiply(price)) < 0) {
                app.log("Not enough " + pair.getSymbolQuote() + " to buy " + df6.format(baseAmount) + " " + pair.getSymbolBase() + ". Need " + df6.format(baseAmount.multiply(price)) + " but have " + df6.format(pair.getQuoteItem().getFreeValue()), true, true);
                SEMAPHORE.release();
                return -1;
            }
            if (!isTestMode) {
                if (isLimitedOrders && (limitedOrderMode == LimitedOrderMode.LOMODE_SELLANDBUY || limitedOrderMode == LimitedOrderMode.LOMODE_BUY)) {
                    //price = price / (1 + 0.01f * tradeComissionPercent);
                    result = client.order(true, false, symbolPair, baseAmount, price);
                } else {
                    result = client.order(true, true, symbolPair, baseAmount, price);
                }
                if (result > 0) {
                    pair.startBuyTransaction(baseAmount, baseAmount.multiply(price));
                    app.log("Order id = " + result, false, true);
                    Thread.sleep(550);
                    Order mord = client.getOrderStatus(symbolPair, result);
                    if (mord.getStatus() != OrderStatus.NEW && mord.getStatus() != OrderStatus.PARTIALLY_FILLED && mord.getStatus() != OrderStatus.FILLED) {
                        pair.rollbackTransaction();
                        app.log("Order cancelled!", true, true);
                        SEMAPHORE.release();
                        return -1;
                    } else if (mord.getStatus() == OrderStatus.FILLED) {
                        pair.confirmTransaction();
                        BigDecimal lastTradePrice = client.getLastTradePrice(symbolPair, true);
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
                        client.cancelOrder(symbolPair, order_id);
                        app.log("Canceling LimitOrder "+order_id+"!", true, true);
                    });
                    Thread.sleep(750);
                }
                if (isLimitedOrders && (limitedOrderMode == LimitedOrderMode.LOMODE_SELLANDBUY || limitedOrderMode == LimitedOrderMode.LOMODE_SELL)) {
                    result = client.order(false, false, symbolPair, baseAmount, price);
                } else {
                    result = client.order(false, true, symbolPair, baseAmount, price);
                }
                if (result > 0) {
                    pair.startSellTransaction(baseAmount, baseAmount.multiply(price));
                    app.log("Order id = " + result, false, true);
                    Thread.sleep(550);
                    Order mord = client.getOrderStatus(symbolPair, result);
                    if (mord.getStatus() != OrderStatus.NEW && mord.getStatus() != OrderStatus.PARTIALLY_FILLED && mord.getStatus() != OrderStatus.FILLED) {
                        pair.rollbackTransaction();
                        app.log("Order cancelled!", true, true);
                        SEMAPHORE.release();
                        return -1;
                    } else if (mord.getStatus() == OrderStatus.FILLED) {
                        pair.confirmTransaction();
                        BigDecimal lastTradePrice = client.getLastTradePrice(symbolPair, false);
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

    public void cancelOrder(String symbolPair, long limitOrderId) {
        if (!isTestMode) {
            client.cancelOrder(symbolPair, limitOrderId);
        } else {
            currencyPairItem pair = pair_map.get(symbolPair);
            pair.rollbackTransaction();
            updatePairText(symbolPair, false);
        }
    }
    
    public void setPairPrice(String symbolPair, BigDecimal price) {
        currencyPairItem pair = pair_map.get(symbolPair);
        if (pair != null) {
            pair.setPrice(price);
            updatePairText(symbolPair, false);
        }
    }

    public void finishOrder(String symbolPair, boolean isok, BigDecimal sold_price) {
        currencyPairItem pair = pair_map.get(symbolPair);
        if (pair != null) {
            if (isok) {
                pair.setLastOrderPrice(sold_price);
                pair.confirmTransaction();
            } else {
                pair.rollbackTransaction();
            }
        }
    }
    
    public void finishOrderPart(String symbolPair, BigDecimal sold_price, BigDecimal sold_qty) {
        currencyPairItem pair = pair_map.get(symbolPair);
        if (pair != null) {
            pair.setLastOrderPrice(sold_price);
            pair.confirmTransactionPart(sold_qty, sold_qty.multiply(sold_price));
        }
    }
    
    public void updateAllBalances(boolean updateLists) {
        if (client != null) {
            List<AssetBalance> allBalances = client.getAllBalances();
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
                        if (curr.getBaseItem().getListIndex() >= 0) {
                            listProfitModel.set(curr.getBaseItem().getListIndex(), curr.getBaseItem().toString());
                        }
                    }
                }
                if (!tradeComissionCurrency.isEmpty()) {
                    currencyItem curr = curr_map.get(tradeComissionCurrency);
                    if (curr != null && curr.getListIndex() >= 0) {
                        listProfitModel.set(curr.getListIndex(), curr.toString());
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
        boolean symbol_updated = false;
        for (Map.Entry<String, currencyPairItem> entry : pair_map.entrySet()) {
            currencyPairItem curr = entry.getValue();
            if (curr != null && curr.getSymbolBase().equals(symbolBase)) {
                symbol_updated = true;
                if (curr.getListIndex() >= 0) {
                    listCurrenciesModel.set(curr.getListIndex(), curr.toString());
                }
                if (curr.getQuoteItem().getListIndex() >= 0) {
                    listProfitModel.set(curr.getQuoteItem().getListIndex(), curr.toString());
                }
                if (curr.getBaseItem().getListIndex() >= 0) {
                    listProfitModel.set(curr.getBaseItem().getListIndex(), curr.toString());
                }
            }
        }
        if (!symbol_updated) {
            currencyItem curr = curr_map.get(symbolBase);
            if (curr != null && curr.getListIndex() >= 0) {
                listProfitModel.set(curr.getListIndex(), curr.toString());
            }
        }
    }
    public void updatePairText(String symbolPair, boolean updateBalance) {
        placeOrUpdatePair("", "", symbolPair, updateBalance);
    }

    public void placeOrUpdatePair(String symbolBase, String symbolQuote, String symbolPair, boolean updateBalance) {
        try {
            SEMAPHORE_ADDCOIN.acquire();
            symbolPair = symbolPair.toUpperCase();
            currencyPairItem cpair = pair_map.get(symbolPair);
            if (cpair == null) {
                symbolBase = symbolBase.toUpperCase();
                symbolQuote = symbolQuote.toUpperCase();
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

    public void setClient(TradingAPIAbstractInterface _client) {
        client = _client;
    }
    public TradingAPIAbstractInterface getClient() {
        return client;
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
        tradeMinProfitPercent = tradeComissionPercent.multiply(BigDecimal.valueOf(2));
    }

    void removeCurrencyPair(String symbolPair) {
        if (pair_map.containsKey(symbolPair)) {
            int list_index_to_remove = pair_map.get(symbolPair).getListIndex();
            listCurrenciesModel.remove(list_index_to_remove);
            pair_map.remove(symbolPair);
            pair_map.entrySet().stream().filter((entry) -> (entry.getValue().getListIndex() > list_index_to_remove)).forEachOrdered((entry) -> {
                entry.getValue().setListIndex(entry.getValue().getListIndex() - 1);
            });
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

    /**
     * @return the stopLossPercent
     */
    public BigDecimal getStopLossPercent() {
        return stopLossPercent;
    }

    /**
     * @param stopLossPercent the stopLossPercent to set
     */
    public void setStopLossPercent(BigDecimal stopLossPercent) {
        this.stopLossPercent = stopLossPercent;
    }

    /**
     * @return the stopGainPercent
     */
    public BigDecimal getStopGainPercent() {
        return stopGainPercent;
    }

    /**
     * @param stopGainPercent the stopGainPercent to set
     */
    public void setStopGainPercent(BigDecimal stopGainPercent) {
        this.stopGainPercent = stopGainPercent;
    }
    
    /**
     * @return the lowHold
     */
    public boolean isLowHold() {
        return lowHold;
    }

    /**
     * @param lowHold the lowHold to set
     */
    public void setLowHold(boolean lowHold) {
        this.lowHold = lowHold;
    }

    /**
     * @return the limitedOrderMode
     */
    public LimitedOrderMode getLimitedOrderMode() {
        return limitedOrderMode;
    }

    /**
     * @param limitedOrderMode the limitedOrderMode to set
     */
    public void setLimitedOrderMode(LimitedOrderMode limitedOrderMode) {
        this.limitedOrderMode = limitedOrderMode;
    }

    /**
     * @return the tradeMinProfitPercent
     */
    public BigDecimal getTradeMinProfitPercent() {
        return tradeMinProfitPercent;
    }

    /**
     * @return the autoStrategies
     */
    public List<String> getAutoStrategies() {
        return autoStrategies;
    }

    /**
     * @param autoStrategies the autoStrategies to set
     */
    public void setAutoStrategies(List<String> autoStrategies) {
        this.autoStrategies = autoStrategies;
    }

    /**
     * @return the autoWalkForward
     */
    public boolean isAutoWalkForward() {
        return autoWalkForward;
    }

    /**
     * @param autoWalkForward the autoWalkForward to set
     */
    public void setAutoWalkForward(boolean autoWalkForward) {
        this.autoWalkForward = autoWalkForward;
    }

    /**
     * @return the autoPickStrategyParams
     */
    public boolean isAutoPickStrategyParams() {
        return autoPickStrategyParams;
    }

    /**
     * @param autoPickStrategyParams the autoPickStrategyParams to set
     */
    public void setAutoPickStrategyParams(boolean autoPickStrategyParams) {
        this.autoPickStrategyParams = autoPickStrategyParams;
    }
}
