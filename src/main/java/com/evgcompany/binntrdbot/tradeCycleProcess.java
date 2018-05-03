/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.account.Order;
import com.evgcompany.binntrdbot.analysis.CoinCycleController;
import com.evgcompany.binntrdbot.api.TradingAPIAbstractInterface;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author EVG_adm_T
 */
public class tradeCycleProcess extends PeriodicProcessThread {
    
    TradingAPIAbstractInterface client = null;
    CoinCycleController cycleController = null;

    private String symbol;
    private String baseAssetSymbol;
    private String quoteAssetSymbol;
    private String mainAsset;

    private boolean isBuy;
    private final List<CoinFilters> filters = new ArrayList<>();
    private CoinFilters filter = null;

    private final List<String> cycle = new ArrayList<>();
    private final List<BigDecimal> prices = new ArrayList<>();
    private int cycleStep;

    private long limitOrderId = 0;
    private tradeProfitsController profitsChecker = null;
    private long startMillis = 0;
    private long lastOrderMillis = 0;
    
    private BigDecimal last_ordered_amount;
    private BigDecimal last_ordered_price;
    private BigDecimal current_order_amount;
    private BigDecimal current_order_price;
    
    private BigDecimal initialQty = null;
    private String initialAsset = null;
    private double profitAim;
    
    private boolean reverting = false;
    
    private static final DecimalFormat df8 = new DecimalFormat("0.########");

    public tradeCycleProcess(CoinCycleController cycleController) {
        this.cycleController = cycleController;
        client = cycleController.getClient();
        profitsChecker = cycleController.getPairProcessController().getProfitsChecker();
        startMillis = System.currentTimeMillis();
        isBuy = false;
        mainAsset = "";
    }

    public void addChain(String pair, double price) {
        cycle.add(pair);
        prices.add(BigDecimal.valueOf(price));
    }
    public void addChain(String pair, BigDecimal price) {
        cycle.add(pair);
        prices.add(price);
    }

    private void doPreStep() {
        for(int i = 0; i<cycle.size(); i++) {
            String csymbol = cycle.get(i);
            csymbol = csymbol.substring(0, csymbol.indexOf(" ")).trim();
            CoinFilters cfilter = new CoinFilters(csymbol, client);
            cfilter.logFiltersInfo();
            filters.add(cfilter);
            profitsChecker.placeOrUpdatePair(cfilter.getBaseAssetSymbol(), cfilter.getQuoteAssetSymbol(), csymbol, true);
            profitsChecker.setPairPrice(csymbol, prices.get(i));
        }
        profitsChecker.updateAllPairTexts(!profitsChecker.isTestMode());
    }

    private void doInitWaitNextStep() {
        limitOrderId = 0;
        profitsChecker.updateAllPairTexts(!profitsChecker.isTestMode());
    }
    
    private void doPostStep() {
        for(int i = 0; i<cycle.size(); i++) {
            String csymbol = cycle.get(i);
            csymbol = csymbol.substring(0, csymbol.indexOf(" ")).trim();
            profitsChecker.removeCurrencyPair(csymbol);
        }
    }
    
    private void doNextStep() {
        if (limitOrderId != 0) {
            return;
        }
        if (cycleStep >= 0 && isBuy) {
            profitsChecker.finishOrder(symbol, true, current_order_price);
            profitsChecker.updateAllPairTexts(!profitsChecker.isTestMode());
        }
        if (profitsChecker.isTestMode()) {
            doWait(10000);
        }
        cycleStep++;
        if (cycleStep >= cycle.size()) {
            doStop();
            mainApplication.getInstance().log("Cycle "+cycle+" is finished!", true, true);
            return;
        }
        lastOrderMillis = System.currentTimeMillis();
        symbol = cycle.get(cycleStep);
        isBuy = symbol.matches("^.* BUY$");
        symbol = symbol.substring(0, symbol.indexOf(" ")).trim();
        filter = filters.get(cycleStep);
        baseAssetSymbol = filter.getBaseAssetSymbol();
        quoteAssetSymbol = filter.getQuoteAssetSymbol();
        if (mainAsset.isEmpty()) {
            mainAsset = isBuy ? quoteAssetSymbol : baseAssetSymbol;
        }
        symbol = baseAssetSymbol+quoteAssetSymbol;
        
        profitsChecker.placeOrUpdatePair(baseAssetSymbol, quoteAssetSymbol, symbol, true);
        
        current_order_price = prices.get(cycleStep);
        profitsChecker.setPairPrice(symbol, current_order_price);
        if (cycleStep == 0) {
            if (isBuy) {
                current_order_amount = profitsChecker.getOrderAssetAmount(quoteAssetSymbol, cycleController.getTradingBalancePercent());
            } else {
                current_order_amount = profitsChecker.getOrderAssetAmount(baseAssetSymbol, cycleController.getTradingBalancePercent());
            }
        } else {
            current_order_amount = last_ordered_amount;
        }
        
        if (!isBuy) {
            profitsChecker.PreBuy(symbol, current_order_amount, current_order_price);
        }
        
        //System.out.println(symbol + " revert paths to "+mainAsset+":");
        //System.out.println("From " + baseAssetSymbol + ": " + cycleController.getRevertPathDescription(baseAssetSymbol, mainAsset));
        //System.out.println("From " + quoteAssetSymbol + ": " + cycleController.getRevertPathDescription(quoteAssetSymbol, mainAsset));
        
        doOrder(baseAssetSymbol, quoteAssetSymbol, isBuy, false, current_order_price, current_order_amount);
    }
    
    private void orderSuccessfullyFinish() {
        limitOrderId = 0;
        profitsChecker.finishOrder(symbol, true, last_ordered_price);
        if (!profitsChecker.isTestMode()) {
            profitsChecker.updateAllBalances(true);
        } else {
            profitsChecker.updateAllPairTexts(!profitsChecker.isTestMode());
        }
    }
    private void orderAbort() {
        if (limitOrderId > 0) {
            if (!profitsChecker.isTestMode()) {
                profitsChecker.cancelOrder(symbol, limitOrderId);
            }
            doWait(1000);
        }
        profitsChecker.finishOrder(symbol, false, null);
        limitOrderId = -1;
        
        revertCoinCycle();
        
        if (!profitsChecker.isTestMode()) {
            profitsChecker.updateAllBalances(true);
        }
        doStop();
        mainApplication.getInstance().log("Cycle "+cycle+" is aborted!", true, true);
    }
    
    private void revertCoinCycle() {
        
        reverting = true;
        
        BigDecimal successPart = last_ordered_amount;
        BigDecimal failurePart = current_order_amount.subtract(successPart);
        
        String successCoin = isBuy ? baseAssetSymbol : quoteAssetSymbol;
        String failureCoin = isBuy ? quoteAssetSymbol : baseAssetSymbol;
        
        List<String> successRevertPath = cycleController.getRevertPathDescription(successCoin, mainAsset);
        List<String> failureRevertPath = cycleController.getRevertPathDescription(failureCoin, mainAsset);
        
        if (successRevertPath != null && successPart.compareTo(BigDecimal.ZERO) > 0) {
            doRevertOrder(successPart, successRevertPath);
        }
        if (failureRevertPath != null && failurePart.compareTo(BigDecimal.ZERO) > 0) {
            doRevertOrder(failurePart, failureRevertPath);
        }
        
        profitsChecker.finishOrder(symbol, true, last_ordered_price);
        if (!profitsChecker.isTestMode()) {
            profitsChecker.updateAllBalances(true);
        } else {
            profitsChecker.updateAllPairTexts(!profitsChecker.isTestMode());
        }
    }
    
    private void doRevertOrder(BigDecimal amount, List<String> path) {
        System.out.println("Reverting " + amount + " " + path);
        if (path.isEmpty()) return;
        if (path.size() > 1) {
            // @todo
        }
        if (cycleStep >= 0 && isBuy) {
            profitsChecker.finishOrder(symbol, true, current_order_price);
            profitsChecker.updateAllPairTexts(!profitsChecker.isTestMode());
        }
        symbol = path.get(0);
        isBuy = symbol.matches("^.* BUY$");
        symbol = symbol.substring(0, symbol.indexOf(" ")).trim();
        filter = new CoinFilters(symbol, client);
        filter.logFiltersInfo();
        profitsChecker.placeOrUpdatePair(filter.getBaseAssetSymbol(), filter.getQuoteAssetSymbol(), symbol, true);
        baseAssetSymbol = filter.getBaseAssetSymbol();
        quoteAssetSymbol = filter.getQuoteAssetSymbol();
        current_order_amount = amount;
        if (cycleController.getCoinRatingController().getCoinInfo().getLastPrices().containsKey(symbol)) {
            current_order_price = BigDecimal.valueOf(cycleController.getCoinRatingController().getCoinInfo().getLastPrices().get(symbol));
        }
        if (!isBuy) {
            profitsChecker.PreBuy(symbol, current_order_amount, current_order_price);
        }
        doOrder(baseAssetSymbol, quoteAssetSymbol, isBuy, true, current_order_price, current_order_amount);
        doWait(2000);
    }
    
    private void checkOrders() {
        if (limitOrderId <= 0) {
            return;
        }
        Order order = client.getOrderStatus(symbol, limitOrderId);

        System.out.println(order);
        
        if (order == null) {
            return;
        }
        
        if (order.getStatus() == OrderStatus.PARTIALLY_FILLED && 
            Double.parseDouble(order.getExecutedQty()) < Double.parseDouble(order.getOrigQty())
        ) {
            return;
        }

        if (
            order.getStatus() == OrderStatus.FILLED || 
            order.getStatus() == OrderStatus.PARTIALLY_FILLED || 
            order.getStatus() == OrderStatus.CANCELED ||
            order.getStatus() == OrderStatus.EXPIRED ||
            order.getStatus() == OrderStatus.REJECTED
        ) {
            if (order.getStatus() == OrderStatus.FILLED && Double.parseDouble(order.getExecutedQty()) >= Double.parseDouble(order.getOrigQty())) {
                last_ordered_price = new BigDecimal(order.getPrice());
                last_ordered_amount = new BigDecimal(order.getExecutedQty());
                if (order.getSide() == OrderSide.SELL) last_ordered_amount = last_ordered_amount.multiply(last_ordered_price);
                orderSuccessfullyFinish();
            } else {
                last_ordered_amount = new BigDecimal(order.getExecutedQty());
                if (order.getSide() == OrderSide.SELL) last_ordered_amount = last_ordered_amount.multiply(new BigDecimal(order.getPrice()));
                limitOrderId = 0;
                if (!reverting) orderAbort();
            }
        } else if (cycleStep >= 1 && Double.parseDouble(order.getExecutedQty()) <= 0 && cycleController.getAbortProfit(initialQty, new BigDecimal(order.getOrigQty()), initialAsset, baseAssetSymbol, quoteAssetSymbol) > profitAim) {
            mainApplication.getInstance().log("We'll have more profit if abort this "+symbol+" cycle order...");
            last_ordered_amount = new BigDecimal(order.getExecutedQty());
            if (order.getSide() == OrderSide.SELL) last_ordered_amount = last_ordered_amount.multiply(new BigDecimal(order.getPrice()));
            if (!reverting) orderAbort();
        } else {
            if (cycleController.isUseStopLimited() && (System.currentTimeMillis()-lastOrderMillis) > cycleController.getStopLimitTimeout() * 1000) {
                mainApplication.getInstance().log("We wait too long. Need to stop this "+symbol+"'s cycle order...");
                last_ordered_amount = new BigDecimal(order.getExecutedQty());
                if (order.getSide() == OrderSide.SELL) last_ordered_amount = last_ordered_amount.multiply(new BigDecimal(order.getPrice()));
                if (!reverting) orderAbort();
            }
        }
    }
    
    private void doOrder(String baseSymbol, String quoteSymbol, boolean isBuying, boolean isMarket, BigDecimal price, BigDecimal amount) {
        String symbol_order = baseSymbol+quoteSymbol;
        if (isBuying) {
            filter.setCurrentPrice(price);
            filter.setCurrentAmount(amount);
            filter.prepareForBuy(profitsChecker);
            BigDecimal tobuy_price = filter.getCurrentPrice();
            BigDecimal tobuy_amount = filter.getCurrentAmount();
            if (profitsChecker.canBuy(symbol_order, tobuy_amount, tobuy_price)) {
                
                if (initialQty == null && initialAsset == null) {
                    initialAsset = quoteSymbol;
                    initialQty = tobuy_amount.multiply(tobuy_price);
                }
                
                mainApplication.getInstance().log("BYING " + df8.format(tobuy_amount) + " " + baseSymbol + " for " + quoteSymbol + " (price=" + df8.format(tobuy_price) + ")", true, true);
                long result = profitsChecker.Buy(symbol_order, tobuy_amount, isMarket ? null : tobuy_price);
                if (result >= 0) {
                    last_ordered_amount = tobuy_amount;
                    last_ordered_price = tobuy_price;
                    limitOrderId = result;
                    mainApplication.getInstance().log("Successful!", true, true);
                    lastOrderMillis = System.currentTimeMillis();
                    if (limitOrderId == 0) {
                        if (!reverting) doInitWaitNextStep();
                    }
                } else {
                    mainApplication.getInstance().log("Error!", true, true);
                    last_ordered_amount = BigDecimal.ZERO;
                    last_ordered_price = tobuy_price;
                    if (!reverting) orderAbort();
                }
            } else {
                BigDecimal avail = profitsChecker.getAvailableCount(quoteSymbol);
                if (avail == null) avail = BigDecimal.valueOf(-1);
                mainApplication.getInstance().log("Can't buy " + df8.format(tobuy_amount) + " " + baseSymbol + " for " + quoteSymbol + " (price=" + df8.format(tobuy_price) + ")" + " (avail summ = "+avail+")");
                last_ordered_amount = BigDecimal.ZERO;
                last_ordered_price = tobuy_price;
                if (!reverting) orderAbort();
            }
        } else {
            filter.setCurrentPrice(price);
            filter.setCurrentAmount(amount);
            filter.prepareForSell(profitsChecker);
            BigDecimal tosell_price = filter.getCurrentPrice();
            BigDecimal tosell_amount = filter.getCurrentAmount();
            if (profitsChecker.canSell(symbol_order, tosell_amount)) {
                
                if (initialQty == null && initialAsset == null) {
                    initialAsset = baseSymbol;
                    initialQty = tosell_amount;
                }
                
                mainApplication.getInstance().log("SELLING " + df8.format(tosell_amount) + " " + baseSymbol + " for " + quoteSymbol + " (price=" + df8.format(tosell_price) + ")", true, true);
                long result = profitsChecker.Sell(symbol_order, tosell_amount, isMarket ? null : tosell_price, null);
                if (result >= 0) {
                    last_ordered_amount = tosell_amount.multiply(tosell_price);
                    last_ordered_price = tosell_price;
                    limitOrderId = result;
                    mainApplication.getInstance().log("Successful!", true, true);
                    lastOrderMillis = System.currentTimeMillis();
                    if (limitOrderId == 0) {
                        if (!profitsChecker.isTestMode()) {
                            profitsChecker.updateAllBalances(true);
                        }
                        if (!reverting) doInitWaitNextStep();
                    }
                } else {
                    mainApplication.getInstance().log("Error!", true, true);
                    last_ordered_amount = BigDecimal.ZERO;
                    last_ordered_price = tosell_price;
                    if (!reverting) orderAbort();
                }
            } else {
                BigDecimal avail = profitsChecker.getAvailableCount(baseSymbol);
                if (avail == null) avail = BigDecimal.valueOf(-1);
                mainApplication.getInstance().log("Can't sell " + tosell_amount + " " + symbol_order + " (avail summ = "+avail+")", false, true);
                last_ordered_amount = BigDecimal.ZERO;
                last_ordered_price = tosell_price;
                if (!reverting) orderAbort();
            }
        }
    }
    
    @Override
    protected void runStart() {
        limitOrderId = 0;
        cycleStep = -1;

        mainApplication.getInstance().log("thread for cycle " + cycle + " running...");
        mainApplication.getInstance().log("Prices: " + prices);
        mainApplication.getInstance().log("");

        doPreStep();
    }
    
    @Override
    protected void runBody() {
        checkOrders();
        doNextStep();
    }

    @Override
    protected void runFinish() {
        doPostStep();
        mainApplication.getInstance().log("thread for cycle " + cycle + " is stopped...");
    }

    public String getBaseSymbol() {
        return baseAssetSymbol;
    }
    public String getQuoteSymbol() {
        return quoteAssetSymbol;
    }
    
    public boolean isInLimitOrder() {
        return limitOrderId>0;
    }

    /**
     * @return the startMillis
     */
    public long getStartMillis() {
        return startMillis;
    }

    /**
     * @return the profitAim
     */
    public double getProfitAim() {
        return profitAim;
    }

    /**
     * @param profitAim the profitAim to set
     */
    public void setProfitAim(double profitAim) {
        this.profitAim = profitAim;
    }
}
