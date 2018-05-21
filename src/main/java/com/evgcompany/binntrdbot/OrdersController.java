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
import com.evgcompany.binntrdbot.events.PairOrderCheckEvent;
import com.evgcompany.binntrdbot.events.PairOrderEvent;
import com.evgcompany.binntrdbot.misc.NumberFormatter;
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
public class OrdersController extends PeriodicProcessSocketUpdateThread {

    public enum LimitedOrderMode {
        LOMODE_SELL, LOMODE_BUY, LOMODE_SELLANDBUY
    }
    
    private static final OrdersController instance = new OrdersController();
    
    private final mainApplication app;
    private CoinInfoAggregator info = null;
    private BalanceController balance = null;
    
    private static final Semaphore SEMAPHORE = new Semaphore(1, true);
    private static final Semaphore SEMAPHORE_ADDCOIN = new Semaphore(1, true);
    private static final Semaphore SEMAPHORE_CHECK = new Semaphore(1, true);

    private final Map<Long, OrderPairItem> pairOrders = new HashMap<>();
    private final OrderEmulator emulator = new OrderEmulator();
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

    private OrdersController() {
        app = mainApplication.getInstance();
        info = CoinInfoAggregator.getInstance();
        balance = BalanceController.getInstance();
        doExceptionsStop = false;
    }
    
    public static OrdersController getInstance(){
        return instance;
    }
    
    public boolean PreBuySell(Long orderCID, BigDecimal baseAmount, BigDecimal buyPrice/*, BigDecimal sellAmount, BigDecimal sellPrice*/) {
        OrderPairItem pair = pairOrders.get(orderCID);
        pair.preBuyTransaction(baseAmount, baseAmount.multiply(buyPrice), buyPrice, true);
        /*if (sellAmount != null && sellAmount.compareTo(BigDecimal.ZERO) > 0) {
            pair.startSellTransaction(sellAmount, sellAmount.multiply(sellPrice), buyPrice);
        }*/
        updatePairTrade(orderCID, !isTestMode);
        return true;
    }
    
    public void PreBuy(Long orderCID, BigDecimal baseAmount, BigDecimal buyPrice) {
        OrderPairItem pair = pairOrders.get(orderCID);
        pair.preBuyTransaction(baseAmount, baseAmount.multiply(buyPrice), buyPrice, false);
        updatePairTrade(orderCID, !isTestMode);
    }
    
    public long Buy(Long orderCID, BigDecimal baseAmount, BigDecimal price) {
        return Buy(orderCID, baseAmount, price, true);
    }
    
    public long Buy(Long orderCID, BigDecimal baseAmount, BigDecimal price, boolean use_transactions) {
        long result;
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
                price = BigDecimal.valueOf(CoinInfoAggregator.getInstance().getLastPrices().get(pair.getSymbolPair()));
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
                result = client.order(true, isMarket, pair.getSymbolPair(), baseAmount, price);
            } else {
                result = emulator.emulateOrder(true, isMarket, pair.getSymbolPair(), baseAmount, price);
            }
            pair.resetCheckHashOrder();
            pair.setOrderAPIID(result);
            if (result > 0) {
                if (use_transactions)
                    pair.startBuyTransaction(baseAmount, baseAmount.multiply(price), price);
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
        long result;
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
                price = BigDecimal.valueOf(CoinInfoAggregator.getInstance().getLastPrices().get(pair.getSymbolPair()));
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
                result = client.order(false, isMarket, pair.getSymbolPair(), baseAmount, price);
            } else {
                if (OrderToCancel != null && !OrderToCancel.isEmpty()) {
                    OrderToCancel.forEach((order_id)->{
                        emulator.cancelOrder(order_id);
                        app.log("Canceling LimitOrder "+order_id+"!", true, true);
                    });
                    Thread.sleep(750);
                }
                result = emulator.emulateOrder(false, isMarket, pair.getSymbolPair(), baseAmount, price);
            }
            pair.resetCheckHashOrder();
            pair.setOrderAPIID(result);
            if (result > 0) {
                if (use_transactions)
                    pair.startSellTransaction(baseAmount, baseAmount.multiply(price), price);
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

    private void transferLimit(Long orderCID, long orderAPIID) {
        OrderPairItem pair = pairOrders.get(orderCID);
        if (pair == null) return;
        Order order = client.getOrderStatus(pair.getSymbolPair(), orderAPIID);
        if (order == null || order.getStatus() == OrderStatus.CANCELED || order.getStatus() == OrderStatus.FILLED) return;
        BigDecimal orderValue = new BigDecimal(order.getOrigQty()).subtract(new BigDecimal(order.getExecutedQty()));
        if (orderValue.compareTo(pair.getBaseItem().getLimitValue()) < 0) orderValue = pair.getBaseItem().getLimitValue();
        pair.getBaseItem().addFreeValue(orderValue);
        pair.getBaseItem().addLimitValue(orderValue.multiply(BigDecimal.valueOf(-1)));
    }
    
    public boolean cancelOrdersList(Long orderCID, List<Long> ordersToCancel) {
        OrderPairItem pair = pairOrders.get(orderCID);
        if (ordersToCancel != null && !ordersToCancel.isEmpty()) {
            ordersToCancel.forEach((orderId) -> {
                cancelOrder(orderCID, orderId, false);
            });
            doWait(500);
            checkOrder(orderCID, pairOrders.get(orderCID));
        }
        updatePairTrade(orderCID, !isTestMode);
        return true;
    }
    public void cancelOrder(Long orderCID, long orderAPIID, boolean doRecheck) {
        if (pairOrders.containsKey(orderCID) && orderAPIID > 0) {
            if (!isTestMode) {
                client.cancelOrder(pairOrders.get(orderCID).getSymbolPair(), orderAPIID);
            } else {
                if (!emulator.cancelOrder(orderAPIID)) {
                    transferLimit(orderCID, orderAPIID);
                }
            }
            if (doRecheck) {
                doWait(500);
                checkOrder(orderCID, pairOrders.get(orderCID));
            }
        }
    }
    public void cancelOrder(Long orderCID, boolean doRecheck) {
        if (pairOrders.containsKey(orderCID) && pairOrders.get(orderCID).getOrderAPIID() > 0) {
            cancelOrder(orderCID, pairOrders.get(orderCID).getOrderAPIID(), doRecheck);
        }
    }
    public void cancelOrder(Long orderCID) {
        cancelOrder(orderCID, true);
    }
    
    public Order getAPIOrder(Long orderCID) {
        if (pairOrders.containsKey(orderCID) && pairOrders.get(orderCID).getOrderAPIID() > 0) {
            if (!isTestMode) {
                return client.getOrderStatus(pairOrders.get(orderCID).getSymbolPair(), pairOrders.get(orderCID).getOrderAPIID());
            } else {
                return emulator.getEmulatedOrder(pairOrders.get(orderCID).getOrderAPIID());
            }
        }
        return null;
    }
    
    public void setPairOrderPrice(Long orderCID, BigDecimal order_price) {
        OrderPairItem pair = pairOrders.get(orderCID);
        if (pair != null) {
            pair.setLastOrderPrice(order_price);
            updatePairTrade(orderCID, false);
        }
    }

    public void finishOrder(Long orderCID, boolean isok, BigDecimal sold_price) {
        OrderPairItem pair = pairOrders.get(orderCID);
        if (pair != null) {
            if (isok) {
                pair.confirmTransaction();
            } else {
                pair.rollbackTransaction();
            }
            pair.setLastOrderPrice(pair.getPyramidSize() != 0 ? sold_price : null);
            updatePairTradeText(orderCID);
        }
    }
    
    public void finishOrderPart(Long orderCID, BigDecimal sold_price, BigDecimal sold_qty) {
        OrderPairItem pair = pairOrders.get(orderCID);
        if (pair != null) {
            pair.setLastOrderPrice(sold_price);
            pair.confirmTransactionPart(sold_qty, sold_qty.multiply(sold_price));
        }
    }

    public Long registerPairTrade(
        String symbolPair, 
        ControllableOrderProcess process, 
        PairOrderEvent orderEvent, 
        PairOrderCheckEvent orderCancelCheckEvent
    ) {
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
                cpair.setOrderCancelCheckEvent(orderCancelCheckEvent);
                cpair.setOrderProcess(process);
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
        if (isTestMode) {
            return;
        }
        Long pairCID = null;
        long orderId = event.getOrderId();
        String symbol = event.getSymbol().toUpperCase();
        for (Map.Entry<Long, OrderPairItem> entry : pairOrders.entrySet()) {
            if (entry.getValue().getOrderAPIID() == orderId && symbol.equals(entry.getValue().getSymbolPair())) {
                pairCID = entry.getKey();
                break;
            }
        }
        if (pairCID != null && pairOrders.get(pairCID).getOrderEvent() != null) {
            try {
                SEMAPHORE_CHECK.acquire();
                OrderPairItem pairItem = pairOrders.get(pairCID);
                if (pairItem.getOrderAPIID() > 0) {
                    boolean isNew = (pairItem.getLastCheckHashOrder() != null && pairItem.getLastCheckHashOrder().isEmpty()) || 
                            event.getExecutionType() == ExecutionType.NEW;

                    String newHash = getEventHash(event);
                    if (newHash == null || !newHash.equals(pairItem.getLastCheckHashOrder())) {
                        pairItem.setLastCheckHashOrder(newHash);
                        BigDecimal price = new BigDecimal(event.getPrice());
                        if (price.compareTo(BigDecimal.ZERO) <= 0) price = new BigDecimal(event.getPriceOfLastFilledTrade());
                        BigDecimal executedQty = new BigDecimal(event.getAccumulatedQuantity());
                        BigDecimal qty = new BigDecimal(event.getOriginalQuantity());
                        boolean isCanceled = event.getOrderStatus() == OrderStatus.CANCELED || 
                                event.getOrderStatus() == OrderStatus.EXPIRED || 
                                event.getOrderStatus() == OrderStatus.REJECTED;
                        boolean isFinished = event.getOrderStatus() == OrderStatus.FILLED || isCanceled;
                        boolean isBuying = event.getSide() == OrderSide.BUY;

                        if (isFinished) {
                            pairItem.setOrderAPIID(0);
                            pairItem.setLastOrderPrice(null);
                            pairItem.resetCheckHashOrder();
                            BalanceController.getInstance().testPayTradeComissionForPair(executedQty.multiply(price), pairItem.getSymbolPair());
                        }

                        pairItem.getOrderEvent().onOrderUpdate(pairCID, pairItem, isNew, isBuying, isCanceled, isFinished, price, qty, executedQty);
                        updatePairTrade(pairCID, !isTestMode);
                    }
                }
            } catch (InterruptedException ex) {
                Logger.getLogger(OrdersController.class.getName()).log(Level.SEVERE, null, ex);
            }
            SEMAPHORE_CHECK.release();
        }
        if (Double.parseDouble(event.getAccumulatedQuantity()) > 0) {
            mainApplication.getInstance().log("OrderEvent: " + event.getType().name() + " " + event.getSide().name() + " " + event.getSymbol() + "; Qty=" + event.getAccumulatedQuantity() + "; Price=" + event.getPrice(), false, true);
            mainApplication.getInstance().systemSound();
        }
    }
    
    private Order checkOrder(Long orderCID, OrderPairItem pairItem) {
        if (orderCID == null || pairItem == null) return null;
        if (pairItem.getOrderAPIID() <= 0) return null;

        Order result = null;
        try {
            SEMAPHORE_CHECK.acquire();
            Order order = null;
            if (!isTestMode) {
                order = client.getOrderStatus(pairItem.getSymbolPair(), pairItem.getOrderAPIID());
            } else {
                order = emulator.getEmulatedOrder(pairItem.getOrderAPIID());
            }

            if(order != null) {
                boolean isNew = pairItem.getLastCheckHashOrder()!=null && pairItem.getLastCheckHashOrder().isEmpty();

                String newHash = getOrderHash(order);
                if (newHash == null || !newHash.equals(pairItem.getLastCheckHashOrder())) {
                    pairItem.setLastCheckHashOrder(newHash);
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
                        pairItem.setLastOrderPrice(null);
                        pairItem.resetCheckHashOrder();
                        BalanceController.getInstance().testPayTradeComissionForPair(executedQty.multiply(price), order.getSymbol());
                    }
                    if (pairItem.getOrderEvent() != null) {
                        pairItem.getOrderEvent().onOrderUpdate(orderCID, pairItem, isNew, isBuying, isCanceled, isFinished, price, qty, executedQty);
                    }
                    mainApplication.getInstance().log("Checked: " + order.getType().name() + " " + order.getSide().name() + " " + order.getSymbol() + "; Qty=" + executedQty + "; Price=" + price, false, true);
                    updatePairTrade(orderCID, !isTestMode);
                }
                if (isTestMode) emulator.progressOrder(pairItem.getOrderAPIID());
                result = order;
            }
        } catch (InterruptedException ex) {
            Logger.getLogger(OrdersController.class.getName()).log(Level.SEVERE, null, ex);
        }
        SEMAPHORE_CHECK.release();
        
        return result;
    }
    
    private void checkOrders() {
        for (Map.Entry<Long, OrderPairItem> entry : pairOrders.entrySet()) {
            Order checked = checkOrder(entry.getKey(), entry.getValue());
            if (checked != null) {
                if (entry.getValue().getOrderCancelCheckEvent() != null) {
                    if (entry.getValue().getOrderCancelCheckEvent().onOrderCancelCheck(entry.getKey(), entry.getValue(), checked)) {
                        cancelOrder(entry.getKey(), false);
                        doWait(500);
                        checkOrder(entry.getKey(), entry.getValue());
                    }
                } else {
                    doWait(500);
                }
            }
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
    
    public void updatePairTradeText(Long orderCID) {
        OrderPairItem cpair = pairOrders.get(orderCID);
        if (cpair != null && cpair.getListIndex() >= 0) {
            listPairOrdersModel.set(cpair.getListIndex(), cpair.toString());
        }
    }
    
    @Override
    protected void initSocket() {
        if (!isTestMode) {
            setSocket(client.OnOrderEvent(null, this::orderEvent, this::socketClosed));
        }
    }
    
    @Override
    protected void runStart() {
        try {
            SEMAPHORE_ADDCOIN.acquire();
            info.StartAndWaitForInit();
            balance.StartAndWaitForInit();
        } catch (InterruptedException ex) {
            Logger.getLogger(OrdersController.class.getName()).log(Level.SEVERE, null, ex);
        }
        SEMAPHORE_ADDCOIN.release();
    }

    @Override
    protected void runBody() {
        checkOrders();
    }

    @Override
    protected void runFinish() {
        mainApplication.getInstance().log("Stopping orders update thread...");
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

    public ControllableOrderProcess getNthControllableOrderProcess(int n) {
        for (Map.Entry<Long, OrderPairItem> entry : pairOrders.entrySet()) {
            if (entry.getValue().getListIndex() == n) {
                return entry.getValue().getOrderProcess();
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
