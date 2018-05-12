/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import com.binance.api.client.domain.ExecutionType;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.event.OrderTradeUpdateEvent;
import com.evgcompany.binntrdbot.api.TradingAPIAbstractInterface;
import com.evgcompany.binntrdbot.coinrating.CoinInfoAggregator;
import com.evgcompany.binntrdbot.coinrating.CoinRatingController;
import com.evgcompany.binntrdbot.events.PairOrderEvent;
import com.evgcompany.binntrdbot.misc.NumberFormatter;
import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Semaphore;
import javax.swing.DefaultListModel;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author EVG_Adminer
 */
public class OrdersController extends PeriodicProcessThread {

    public enum LimitedOrderMode {
        LOMODE_SELL, LOMODE_BUY, LOMODE_SELLANDBUY
    }
    
    private static final OrdersController instance = new OrdersController();
    
    private final mainApplication app;
    private CoinInfoAggregator info = null;
    private BalanceController balance = null;
    
    private static final Semaphore SEMAPHORE = new Semaphore(1, true);
    private static final Semaphore SEMAPHORE_ADDCOIN = new Semaphore(1, true);

    private final Map<Long, OrderPairItem> pairOrders = new HashMap<>();
    private long lastOrderId = 0;

    private final DefaultListModel<String> listPairOrdersModel = new DefaultListModel<>();

    private boolean isTestMode = false;
    private boolean isLimitedOrders = false;
    private LimitedOrderMode limitedOrderMode = LimitedOrderMode.LOMODE_SELLANDBUY;

    private BigDecimal tradeMinProfitPercent;
    
    private BigDecimal stopLossPercent = null;
    private BigDecimal stopGainPercent = null;
    private boolean lowHold = true;
    
    private List<String> autoStrategies = new ArrayList<>();
    private boolean autoWalkForward = false;
    private boolean autoPickStrategyParams = false;

    private TradingAPIAbstractInterface client = null;
    private Closeable ordersSocket = null;

    private OrdersController() {
        app = mainApplication.getInstance();
        info = CoinInfoAggregator.getInstance();
        balance = BalanceController.getInstance();
    }
    
    public static OrdersController getInstance(){
        return instance;
    }
    
    public long PreBuySell(Long orderCID, BigDecimal baseAmount, BigDecimal buyPrice, BigDecimal sellAmount, BigDecimal sellPrice) {
        long result = 0;
        OrderPairItem pair = pairOrders.get(orderCID);
        pair.setPrice(buyPrice);
        pair.preBuyTransaction(baseAmount, baseAmount.multiply(buyPrice), true);
        if (sellAmount != null && sellAmount.compareTo(BigDecimal.ZERO) > 0) {
            pair.startSellTransaction(sellAmount, sellAmount.multiply(sellPrice));
            if (isTestMode) {
                result = 1;
            }
        }
        updatePairTrade(orderCID, !isTestMode);
        return result;
    }
    
    public void PreBuy(Long orderCID, BigDecimal baseAmount, BigDecimal buyPrice) {
        OrderPairItem pair = pairOrders.get(orderCID);
        pair.setPrice(buyPrice);
        pair.preBuyTransaction(baseAmount, baseAmount.multiply(buyPrice), false);
        updatePairTrade(orderCID, !isTestMode);
    }
    
    public long Buy(Long orderCID, BigDecimal baseAmount, BigDecimal price) {
        return Buy(orderCID, baseAmount, price, true);
    }
    
    public long Buy(Long orderCID, BigDecimal baseAmount, BigDecimal price, boolean use_transactions) {
        long result = 0;
        try {
            SEMAPHORE.acquire();
            OrderPairItem pair = pairOrders.get(orderCID);
            if (pair == null) {
                app.log("Pair for order " + orderCID + " not found!");
                SEMAPHORE.release();
                return -1;
            }
            if (baseAmount == null) {
                app.log("Amount for BUY order " + pair.getSymbolPair() + " (" + orderCID + ") is NULL!");
                SEMAPHORE.release();
                return -1;
            }
            boolean isMarket = (price == null) || !(isLimitedOrders && (limitedOrderMode == LimitedOrderMode.LOMODE_SELLANDBUY || limitedOrderMode == LimitedOrderMode.LOMODE_BUY));
            if (isMarket) {
                price = pair.getPrice();
            }
            if (price == null) {
                app.log("Price for BUY order " + pair.getSymbolPair() + " (" + orderCID + ") is NULL!");
                SEMAPHORE.release();
                return -1;
            }
            if (pair.getQuoteItem().getFreeValue().compareTo(baseAmount.multiply(price)) < 0) {
                app.log("Not enough " + pair.getSymbolQuote() + " to buy " + NumberFormatter.df6.format(baseAmount) + " " + pair.getSymbolBase() + ". Need " + NumberFormatter.df6.format(baseAmount.multiply(price)) + " but have " + NumberFormatter.df6.format(pair.getQuoteItem().getFreeValue()), true, true);
                SEMAPHORE.release();
                return -1;
            }
            if (!isTestMode) {
                if (isMarket) {
                    result = client.order(true, true, pair.getSymbolPair(), baseAmount, price);
                } else {
                    result = client.order(true, false, pair.getSymbolPair(), baseAmount, price);
                }
            } else {
                result = 1;
            }
            pair.resetCheckHashOrder();
            pair.setOrderAPIID(result);
            if (result > 0) {
                if (use_transactions)
                    pair.startBuyTransaction(baseAmount, baseAmount.multiply(price));
                app.log("Order id = " + result, false, true);
                Thread.sleep(550);
                checkOrder(orderCID, pair);
            } else {
                app.log("Order error!", true, true);
            }
            SEMAPHORE.release();
        } catch (Exception e) {
            e.printStackTrace(System.out);
            mainApplication.getInstance().log("EXCEPTION in " + getClass() + " - " + e.getClass() + ": " + e.getLocalizedMessage(), false, true);
            SEMAPHORE.release();
            return -1;
        }
        return result;
    }

    public long Sell(Long orderCID, BigDecimal baseAmount, BigDecimal price, List<Long> OrderToCancel) {
        return Sell(orderCID, baseAmount, price, OrderToCancel, true);
    }
    
    public long Sell(Long orderCID, BigDecimal baseAmount, BigDecimal price, List<Long> OrderToCancel, boolean use_transactions) {
        long result = 0;
        try {
            SEMAPHORE.acquire();
            OrderPairItem pair = pairOrders.get(orderCID);
            if (pair == null) {
                app.log("Pair for order " + orderCID + " not found!");
                SEMAPHORE.release();
                return -1;
            }
            if (baseAmount == null) {
                app.log("Amount for SELL order " + pair.getSymbolPair() + " (" + orderCID + ") is NULL!");
                SEMAPHORE.release();
                return -1;
            }
            boolean isMarket = (price == null) || !(isLimitedOrders && (limitedOrderMode == LimitedOrderMode.LOMODE_SELLANDBUY || limitedOrderMode == LimitedOrderMode.LOMODE_SELL));
            if (isMarket) {
                price = pair.getPrice();
            }
            if (price == null) {
                app.log("Price for SELL order " + pair.getSymbolPair() + " (" + orderCID + ") is NULL!");
                SEMAPHORE.release();
                return -1;
            }
            if (pair.getBaseItem().getFreeValue().compareTo(baseAmount) < 0) {
                app.log("Not enough " + pair.getSymbolBase() + " to sell (" + NumberFormatter.df6.format(pair.getBaseItem().getFreeValue()) + " < " + NumberFormatter.df6.format(baseAmount) + ")", true, true);
                SEMAPHORE.release();
                return -1;
            }
            if (!isTestMode) {
                if (OrderToCancel != null && !OrderToCancel.isEmpty()) {
                    OrderToCancel.forEach((order_id)->{
                        client.cancelOrder(pair.getSymbolPair(), order_id);
                        app.log("Canceling LimitOrder "+order_id+"!", true, true);
                    });
                    Thread.sleep(750);
                }
                if (isMarket) {
                    result = client.order(false, true, pair.getSymbolPair(), baseAmount, price);
                } else {
                    result = client.order(false, false, pair.getSymbolPair(), baseAmount, price);
                }
            } else {
                result = 1;
            }
            pair.resetCheckHashOrder();
            pair.setOrderAPIID(result);
            if (result > 0) {
                if (use_transactions)
                    pair.startSellTransaction(baseAmount, baseAmount.multiply(price));
                app.log("Order id = " + result, false, true);
                Thread.sleep(550);
                checkOrder(orderCID, pair);
            } else {
                app.log("Order error!", true, true);
            }
            SEMAPHORE.release();
        } catch (Exception e) {
            e.printStackTrace(System.out);
            mainApplication.getInstance().log("EXCEPTION in " + getClass() + " - " + e.getClass() + ": " + e.getLocalizedMessage(), false, true);
            SEMAPHORE.release();
            return -1;
        }
        return result;
    }

    public void cancelOrder(Long orderCID, long limitOrderId) {
        OrderPairItem pair = pairOrders.get(orderCID);
        if (!isTestMode) {
            client.cancelOrder(pair.getSymbolPair(), limitOrderId);
        } else {
            pair.rollbackTransaction();
            updatePairTrade(orderCID, false);
        }
    }
    
    public void setPairOrderCurrentPrice(Long orderCID, BigDecimal price) {
        OrderPairItem pair = pairOrders.get(orderCID);
        if (pair != null) {
            pair.setPrice(price);
            updatePairTrade(orderCID, false);
        }
    }
    public void setPairOrderPrices(Long orderCID, BigDecimal price, BigDecimal order_price) {
        OrderPairItem pair = pairOrders.get(orderCID);
        if (pair != null) {
            pair.setPrice(price);
            pair.setLastOrderPrice(order_price);
            updatePairTrade(orderCID, false);
        }
    }

    public void finishOrder(Long orderCID, boolean isok, BigDecimal sold_price) {
        OrderPairItem pair = pairOrders.get(orderCID);
        if (pair != null) {
            if (isok) {
                pair.setLastOrderPrice(sold_price);
                pair.confirmTransaction();
            } else {
                pair.rollbackTransaction();
            }
        }
    }
    
    public void finishOrderPart(Long orderCID, BigDecimal sold_price, BigDecimal sold_qty) {
        OrderPairItem pair = pairOrders.get(orderCID);
        if (pair != null) {
            pair.setLastOrderPrice(sold_price);
            pair.confirmTransactionPart(sold_qty, sold_qty.multiply(sold_price));
        }
    }
    
    /*public void updateAllBalances(boolean updateLists) {
        if (client != null) {
            List<AssetBalance> allBalances = client.getAllBalances();
            for (int i = 0; i < allBalances.size(); i++) {
                String symbol = allBalances.get(i).getAsset().toUpperCase();
                if (coins.containsKey(symbol)) {
                    CoinBalanceItem curr = coins.get(symbol);
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
                for (Map.Entry<String, OrderPairItem> entry : pairOrders.entrySet()) {
                    OrderPairItem curr = entry.getValue();
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
                if (client.getTradeComissionCurrency() != null) {
                    CoinBalanceItem curr = coins.get(client.getTradeComissionCurrency());
                    if (curr != null && curr.getListIndex() >= 0) {
                        listProfitModel.set(curr.getListIndex(), curr.toString());
                    }
                }
            }
        }
    }

    public void updateAllBalances() {
        updateAllBalances(true);
    }*/

    public void updateBaseSymbolText(String symbolBase, boolean updateBalance) {
        if (updateBalance) {
            balance.updateAllBalances();
        }
        boolean symbol_updated = false;
        for (Map.Entry<Long, OrderPairItem> entry : pairOrders.entrySet()) {
            OrderPairItem curr = entry.getValue();
            if (curr != null && curr.getSymbolBase().equals(symbolBase)) {
                symbol_updated = true;
                if (curr.getListIndex() >= 0) {
                    listPairOrdersModel.set(curr.getListIndex(), curr.toString());
                }
                if (curr.getQuoteItem().getListIndex() >= 0) {
                    balance.updateCoinText(curr.getQuoteItem().getSymbol());
                }
                if (curr.getBaseItem().getListIndex() >= 0) {
                    balance.updateCoinText(curr.getBaseItem().getSymbol());
                }
            }
        }
        if (!symbol_updated) {
            balance.updateCoinText(symbolBase);
        }
    }

    public void updateAllPairTexts(boolean updateBalance) {
        pairOrders.forEach((orderCID, item) -> {
            updatePairTrade(orderCID, updateBalance);
        });
    }

    public Long registerPairTrade(String symbolPair, PairOrderEvent orderEvent) {
        Long orderCID = null;
        try {
            SEMAPHORE_ADDCOIN.acquire();
            String symbolBase = info.getPairBaseSymbol(symbolPair);
            String symbolQuote = info.getPairQuoteSymbol(symbolPair);
            if (!symbolBase.isEmpty() && !symbolQuote.isEmpty()) {
                orderCID = ++lastOrderId;
                CoinBalanceItem cbase = balance.getCoinBalanceInfo(symbolBase);
                CoinBalanceItem cquote = balance.getCoinBalanceInfo(symbolQuote);
                OrderPairItem cpair = new OrderPairItem(cbase, cquote, symbolPair);
                cpair.setListIndex(listPairOrdersModel.size());
                cpair.setOrderEvent(orderEvent);
                pairOrders.put(orderCID, cpair);
                balance.updateAllBalances();
                listPairOrdersModel.addElement(cpair.toString());
                balance.setCoinVisible(cbase.getSymbol());
                balance.setCoinVisible(cquote.getSymbol());
            }
            if (orderCID != null) {
                app.log("Trading order pair registered: " + symbolPair + " (" + orderCID + ")");
            } else {
                app.log("Can't register new order pair: " + symbolPair);
            }
        } catch (Exception exx) {
            app.log("Error while register new order pair: " + symbolPair);
            exx.printStackTrace(System.out);
        }
        SEMAPHORE_ADDCOIN.release();
        return orderCID;
    }

    public void unregisterPairTrade(Long orderCID) {
        if (orderCID != null && pairOrders.containsKey(orderCID)) {
            removeCurrencyPair(orderCID);
        }
    }
    
    private String getStringHash(String sourceString) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update(sourceString.getBytes());
            String encryptedString = new String(messageDigest.digest());
            return encryptedString;
        } catch(NoSuchAlgorithmException e) {}
        return null;
    }
    
    private String getOrderHash(Order order) {
        String sourceString = order.getSymbol() + " " + 
                order.getOrderId().toString() + " " + 
                order.getSide() + " " + 
                order.getStatus() + " " + 
                order.getType() + " " + 
                order.getExecutedQty();
        return getStringHash(sourceString);
    }    
    private String getEventHash(OrderTradeUpdateEvent event) {
        String sourceString = event.getSymbol() + " " + 
                event.getOrderId() + " " + 
                event.getSide() + " " + 
                event.getOrderStatus() + " " + 
                event.getType() + " " + 
                event.getAccumulatedQuantity();
        return getStringHash(sourceString);
    }
    
    private void orderEvent(OrderTradeUpdateEvent event) {
        Long pairCID = null;
        long orderId = event.getOrderId();
        String symbol = event.getSymbol().toUpperCase();
        for (Map.Entry<Long, OrderPairItem> entry : pairOrders.entrySet()) {
            if (entry.getValue().getOrderAPIID() == orderId && symbol.equals(entry.getValue().getSymbolPair())) {
                pairCID = entry.getKey();
                break;
            }
        }
        if (pairCID == null || pairOrders.get(pairCID).getOrderEvent() == null) {
            return;
        }
        OrderPairItem pairItem = pairOrders.get(pairCID);

        boolean isNew = (pairItem.getLastCheckHashOrder() != null && pairItem.getLastCheckHashOrder().isEmpty()) || 
                event.getExecutionType() == ExecutionType.NEW;
        
        String newHash = getEventHash(event);
        if (newHash != null && newHash.equals(pairItem.getLastCheckHashOrder())) {
            return;
        } else {
            pairItem.setLastCheckHashOrder(newHash);
        }
        
        BigDecimal price = new BigDecimal(event.getPrice());
        BigDecimal executedQty = new BigDecimal(event.getAccumulatedQuantity());
        BigDecimal qty = new BigDecimal(event.getOriginalQuantity());
        boolean isCanceled = event.getOrderStatus() == OrderStatus.CANCELED || 
                event.getOrderStatus() == OrderStatus.EXPIRED || 
                event.getOrderStatus() == OrderStatus.REJECTED;
        boolean isFinished = event.getOrderStatus() == OrderStatus.FILLED || isCanceled;
        boolean isBuying = event.getSide() == OrderSide.BUY;

        if (isFinished) {
            pairItem.setOrderAPIID(0);
            pairItem.resetCheckHashOrder();
        }
        
        pairItem.getOrderEvent().onOrderUpdate(orderId, pairItem, isNew, isBuying, isCanceled, isFinished, price, qty, executedQty);
        
        if (executedQty.compareTo(BigDecimal.ZERO) > 0) {
            mainApplication.getInstance().log("OrderEvent: " + event.getType().name() + " " + event.getSide().name() + " " + event.getSymbol() + "; Qty=" + event.getAccumulatedQuantity() + "; Price=" + event.getPrice(), true, true);
            mainApplication.getInstance().systemSound();
        }
    }
    
    private void emulateTestOrder(Long orderCID, OrderPairItem pairItem) {
        /*boolean isNew = pairItem.getLastCheckHashOrder()!=null && pairItem.getLastCheckHashOrder().isEmpty();
        
        BigDecimal price = pairItem.getLastOrderPrice();
        BigDecimal executedQty = BigDecimal.ZERO;
        BigDecimal qty = new BigDecimal(order.getOrigQty());
        boolean isCanceled = false;
        boolean isFinished = order.getStatus() == OrderStatus.FILLED || isCanceled;
        boolean isBuying = order.getSide() == OrderSide.BUY;

        if (isFinished) {
            pairItem.setOrderAPIID(0);
            pairItem.resetCheckHashOrder();
        }
        if (pairItem.getOrderEvent() != null) {
            pairItem.getOrderEvent().onOrderUpdate(orderCID, pairItem, isNew, isBuying, isCanceled, isFinished, price, qty, executedQty);
        }
        mainApplication.getInstance().log("Checked: " + order.getType().name() + " " + order.getSide().name() + " " + order.getSymbol() + "; Qty=" + executedQty + "; Price=" + price, true, true);

        
        */
    }
    
    private boolean checkOrder(Long orderCID, OrderPairItem pairItem) {
        if (pairItem.getOrderAPIID() <= 0) return false;
        if (isTestMode && pairItem.getOrderAPIID() == 1) {
            emulateTestOrder(orderCID, pairItem);
            return true;
        }
        Order order = client.getOrderStatus(pairItem.getSymbolPair(), pairItem.getOrderAPIID());
        if(order != null) {
            boolean isNew = pairItem.getLastCheckHashOrder()!=null && pairItem.getLastCheckHashOrder().isEmpty();
            
            String newHash = getOrderHash(order);
            if (newHash != null && newHash.equals(pairItem.getLastCheckHashOrder())) {
                return true;
            } else {
                pairItem.setLastCheckHashOrder(newHash);
            }
            BigDecimal price = new BigDecimal(order.getPrice());
            BigDecimal executedQty = new BigDecimal(order.getExecutedQty());
            BigDecimal qty = new BigDecimal(order.getOrigQty());
            boolean isCanceled = order.getStatus() == OrderStatus.CANCELED || 
                    order.getStatus() == OrderStatus.EXPIRED || 
                    order.getStatus() == OrderStatus.REJECTED;
            boolean isFinished = order.getStatus() == OrderStatus.FILLED || isCanceled;
            boolean isBuying = order.getSide() == OrderSide.BUY;

            if (isFinished) {
                pairItem.setOrderAPIID(0);
                pairItem.resetCheckHashOrder();
            }
            if (pairItem.getOrderEvent() != null) {
                pairItem.getOrderEvent().onOrderUpdate(orderCID, pairItem, isNew, isBuying, isCanceled, isFinished, price, qty, executedQty);
            }
            mainApplication.getInstance().log("Checked: " + order.getType().name() + " " + order.getSide().name() + " " + order.getSymbol() + "; Qty=" + executedQty + "; Price=" + price, true, true);
            return true;
        }
        return false;
    }
    
    private void checkOrders() {
        for (Map.Entry<Long, OrderPairItem> entry : pairOrders.entrySet()) {
            if (checkOrder(entry.getKey(), entry.getValue())) doWait(500);
        }
    }
    
    public void updatePairTrade(Long orderCID, boolean updateBalance) {
        try {
            SEMAPHORE_ADDCOIN.acquire();
            OrderPairItem cpair = pairOrders.get(orderCID);
            if (cpair != null) {
                if (updateBalance) {
                    balance.updateAllBalances();
                }
                if (cpair.getListIndex() >= 0) {
                    listPairOrdersModel.set(cpair.getListIndex(), cpair.toString());
                }
                if (cpair.getBaseItem().getListIndex() >= 0) {
                    balance.updateCoinText(cpair.getBaseItem().getSymbol());
                }
                if (cpair.getQuoteItem().getListIndex() >= 0) {
                    balance.updateCoinText(cpair.getQuoteItem().getSymbol());
                }
            }
        } catch (Exception e) {
        }
        SEMAPHORE_ADDCOIN.release();
    }
    
    @Override
    protected void runStart() {
        info.StartAndWaitForInit();
        balance.StartAndWaitForInit();
        if (!isTestMode) {
            ordersSocket = client.OnOrderEvent(null, this::orderEvent);
        }
    }

    @Override
    protected void runBody() {
        checkOrders();
    }

    @Override
    protected void runFinish() {
        mainApplication.getInstance().log("Stopping orders update thread...");
        if (ordersSocket != null) {
            try {
                ordersSocket.close();
            } catch (IOException ex) {
                Logger.getLogger(CoinRatingController.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * @return the listCurrenciesModel
     */
    public DefaultListModel<String> getListPairOrdersModel() {
        return listPairOrdersModel;
    }

    public void setClient(TradingAPIAbstractInterface _client) {
        client = _client;
        if (client != null) {
            tradeMinProfitPercent = client.getTradeComissionPercent().multiply(BigDecimal.valueOf(2));
            if (info.getClient() == null) {
                info.setClient(client);
            }
        }
    }
    public TradingAPIAbstractInterface getClient() {
        return client;
    }

    public String getNthCurrencyPair(int n) {
        for (Map.Entry<Long, OrderPairItem> entry : pairOrders.entrySet()) {
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

    public void removeCurrencyPair(Long orderCID) {
        if (pairOrders.containsKey(orderCID)) {
            int list_index_to_remove = pairOrders.get(orderCID).getListIndex();
            listPairOrdersModel.remove(list_index_to_remove);
            pairOrders.remove(orderCID);
            pairOrders.entrySet().stream().filter((entry) -> (entry.getValue().getListIndex() > list_index_to_remove)).forEachOrdered((entry) -> {
                entry.getValue().setListIndex(entry.getValue().getListIndex() - 1);
            });
        }
    }

    public OrderPairItem getPairOrderInfo(Long orderCID) {
        return pairOrders.get(orderCID);
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
