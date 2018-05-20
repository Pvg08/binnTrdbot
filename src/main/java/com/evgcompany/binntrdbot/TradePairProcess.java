/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.Trade;
import com.evgcompany.binntrdbot.analysis.NeuralNetworkStockPredictor;
import com.evgcompany.binntrdbot.api.TradingAPIAbstractInterface;
import com.evgcompany.binntrdbot.misc.CurrencyPlot;
import com.evgcompany.binntrdbot.misc.NumberFormatter;
import com.evgcompany.binntrdbot.signal.SignalItem;
import com.evgcompany.binntrdbot.signal.TradeSignalProcessInterface;
import com.evgcompany.binntrdbot.strategies.core.StrategiesController;
import com.evgcompany.binntrdbot.strategies.core.StrategyItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.ta4j.core.Bar;
import org.ta4j.core.Decimal;

/**
 *
 * @author EVG_adm_T
 */
public class TradePairProcess extends AbstractTradePairProcess implements TradeSignalProcessInterface {

    private StrategiesController strategiesController = null;
    private NeuralNetworkStockPredictor predictor = null;
    
    private SignalItem signalItem = null;
    
    private boolean checkOtherStrategies = true;
    private boolean isTryingToSellOnPeak = false;
    private boolean isTryingToBuyOnDip = false;
    private boolean buyOnStart = false;
    private boolean sellOpenOrdersOnPeak = false;
    
    public TradePairProcess(TradingAPIAbstractInterface client, String pair) {
        super(client, pair);
        strategiesController = new StrategiesController(symbol);
    }

    private boolean canBuyForCoinRating() {
        return app.getCoinRatingController() == null || 
                !app.getCoinRatingController().isAlive() || 
                !app.getCoinRatingController().isNoAutoBuysOnDowntrend() || 
                !app.getCoinRatingController().isInDownTrend();
    }
    
    @Override
    protected void onBuySell(boolean isBuying, BigDecimal price, BigDecimal executedQty) {
        super.onBuySell(isBuying, price, executedQty);
        if (isBuying) {
            strategiesController.getTradingRecord().enter(series.getBarCount()-1, Decimal.valueOf(price), Decimal.valueOf(executedQty));
        } else {
            strategiesController.getTradingRecord().exit(series.getBarCount()-1, Decimal.valueOf(price), Decimal.valueOf(executedQty));
        }
    }
    
    @Override
    protected void checkStatus() {
        StrategiesController.StrategiesAction saction = strategiesController.checkStatus((pyramidSize != 0 || !longModeAuto), 
            checkOtherStrategies
        );
        if (saction == StrategiesController.StrategiesAction.DO_ENTER && pyramidSize < pyramidAutoMaxSize) {
            if (canBuyForCoinRating()) doEnter(lastStrategyCheckPrice);
        } else if (saction == StrategiesController.StrategiesAction.DO_EXIT && pyramidSize > -pyramidAutoMaxSize) {
            doExit(lastStrategyCheckPrice, false);
        }
        super.checkStatus();
    }
    
    @Override
    protected void setNewSeries() {
        series = strategiesController.resetSeries();
    }
    
    @Override
    protected void resetSeries() {
        
        if (strategiesController.getMainStrategy().equals("Neural Network")) {
            predictor = new NeuralNetworkStockPredictor(symbol);
            if (!predictor.isHaveNetworkInFile() && predictor.isHaveNetworkInBaseFile()) {
                predictor.toBase();
            }
        }
        
        super.resetSeries();
        
        if (predictor != null && predictor.isHaveNetworkInFile()) {
            predictor.initMinMax(series);
        }
    }
    
    @Override
    protected void resetBars() {
        super.resetBars();
        if (signalItem != null) {
            setMainStrategy("Signal");
            strategiesController.startSignal(signalItem);
        }
        strategiesController.resetStrategies();
    }
    
    @Override
    protected void runStart() {
        super.runStart();

        if (signalItem != null) {
            setMainStrategy("Signal");
            strategiesController.startSignal(signalItem);
        }
        strategiesController.resetStrategies();
        
        if (isTryingToSellOnPeak) {
            initSellAllOnPeak();
        } else if (isTryingToBuyOnDip) {
            initBuyOnDip();
        }
        
        if (buyOnStart && canBuyForCoinRating() && !strategiesController.isShouldLeave()) {
            doBuy();
        }

        app.log(symbol + " initialized. Current price = " + NumberFormatter.df8.format(currentPrice));
    }
    
    @Override
    protected void runBody() {
        super.runBody();
        if(strategiesController.getMainStrategy().equals("Neural Network")) {
            if (predictor != null && !predictor.isLearning() && predictor.isHaveNetworkInFile()) {
                Bar nbar = predictor.predictNextBar(series);
                app.log(symbol + " NEUR prediction = " + NumberFormatter.df8.format(nbar.getClosePrice()));
            }
        }
    }
    
    @Override
    protected void onBarUpdate(Bar nbar, boolean is_fin) {
        super.onBarUpdate(nbar, is_fin);
        if (is_fin && (predictor == null || !predictor.isLearning())) {
            app.log(symbol + " current price = " + NumberFormatter.df8.format(currentPrice));
        }
    }
    
    private void initBuyOnDip() {
        isTryingToSellOnPeak = false;
        stopAfterFinish = true;
        longModeAuto = true;
    }
    
    private void initSellAllOnPeak() {
        isTryingToBuyOnDip = false;
        List<Trade> trades = client.getAllTrades(symbol);
        if (trades != null && !trades.isEmpty()) {
            Collections.sort(
                trades, 
                (Trade lhs, Trade rhs) -> (
                    lhs.getTime() > rhs.getTime() ? -1 : (lhs.getTime() < rhs.getTime()) ? 1 : 0
                )
            );
            BigDecimal avgTradeBuyPrice = BigDecimal.ZERO;

            CoinBalanceItem qbalance = BalanceController.getInstance().getCoinBalanceInfo(baseAssetSymbol);
            BigDecimal free_cnt = qbalance.getFreeValue();
            BigDecimal order_cnt = BigDecimal.ZERO;
            BigDecimal acc_cnt = BigDecimal.ZERO;
            int ordersCount = 0;
            
            for (Trade trade : trades) {
                if (trade.isBuyer() && acc_cnt.compareTo(free_cnt) < 0) {
                    BigDecimal ctprice = new BigDecimal(trade.getPrice());
                    BigDecimal ctvol = new BigDecimal(trade.getQty());
                    if (acc_cnt.add(ctvol).compareTo(free_cnt) > 0) {
                        ctvol = free_cnt.subtract(acc_cnt);
                    }
                    avgTradeBuyPrice = ctprice.multiply(ctvol).add(avgTradeBuyPrice.multiply(acc_cnt)).divide(ctvol.add(acc_cnt), RoundingMode.HALF_UP);
                    ordersCount++;
                    acc_cnt = acc_cnt.add(ctvol);
                }
            }
            
            if (ordersCount == 0) {
                avgTradeBuyPrice = BigDecimal.valueOf(info.getLastPrices().get(symbol));
                ordersCount = 1;
            }
            
            List<Long> ordersToCancel = new ArrayList<>();

            if (sellOpenOrdersOnPeak) {
                List<Order> openOrders = client.getOpenOrders(symbol);
                for (int i=0; i < openOrders.size(); i++) {
                    if (openOrders.get(i).getStatus() == OrderStatus.NEW && openOrders.get(i).getSide() == OrderSide.SELL) {
                        ordersToCancel.add(openOrders.get(i).getOrderId());
                        order_cnt = order_cnt.add(new BigDecimal(openOrders.get(i).getOrigQty()));
                    }
                }
            }

            if (free_cnt.compareTo(BigDecimal.ZERO) > 0 || (order_cnt.compareTo(BigDecimal.ZERO) > 0 && !ordersToCancel.isEmpty())) {
                if (!ordersToCancel.isEmpty()) {
                    app.log("Canceling orders: " + ordersToCancel, true, true);
                    ordersController.cancelOrdersList(orderCID, ordersToCancel);
                    doWait(10000);
                    qbalance = BalanceController.getInstance().getCoinBalanceInfo(baseAssetSymbol);
                    free_cnt = qbalance.getFreeValue();
                }
                if (free_cnt.compareTo(BigDecimal.ZERO) > 0) {
                    boolean result = ordersController.PreBuySell(orderCID, free_cnt, avgTradeBuyPrice/*, currentLimitSellQty, currentLimitSellPrice*/);
                    if (result) {
                        app.log("START WAITING to sell " + NumberFormatter.df8.format(free_cnt) + " " + baseAssetSymbol + " for price more than " + NumberFormatter.df8.format(avgTradeBuyPrice) + " " + quoteAssetSymbol, true, true);
                        inAPIOrder = false;
                        lastBuyPrice = avgTradeBuyPrice;
                        orderBaseAmount = free_cnt;
                        orderQuoteAmount = free_cnt.multiply(avgTradeBuyPrice);
                        orderAvgPrice = avgTradeBuyPrice;
                        stopAfterFinish = true;
                        longModeAuto = true;
                        pyramidSize = ordersCount;
                        ordersController.updatePairTradeText(orderCID);
                        return;
                    } else {
                        app.log("Error in PreBuy method!", true, true);
                    }
                }
            }
        }
        app.log("Can't set SellAllPeak mode for " + symbol, true, true);
    }
    
    public void doNetworkAction(String train, String base) {
        if (strategiesController.getMainStrategy().equals("Neural Network")) {
            predictor = new NeuralNetworkStockPredictor(base.equals("COIN") ? symbol : "");
            switch (train) {
                case "TRAIN":
                    predictor.start();
                    break;
                case "ADDSET":
                    predictor.setSaveTrainData(true);
                    predictor.appendTrainingData(series);
                    break;
                case "STOP":
                    if (predictor.getLearningRule() != null) {
                        predictor.getLearningRule().stopLearning();
                    }   break;
                default:
                    break;
            }
        }
    }
    
    @Override
    public void doShowPlot() {
        StrategyItem item = strategiesController.getMainStrategyItem();
        plot = new CurrencyPlot(symbol, series, strategiesController.getTradingRecord(), item != null ? item.getInitializer() : null);
        for(int i=0; i<strategiesController.getStrategyMarkers().size(); i++) {
            plot.addMarker(
                strategiesController.getStrategyMarkers().get(i).label, 
                strategiesController.getStrategyMarkers().get(i).timeStamp, 
                strategiesController.getStrategyMarkers().get(i).typeIndex
            );
        }
        
        if (predictor != null && predictor.isHaveNetworkInFile()) {
            List<Bar> pbars = predictor.predictPreviousBars(series);
            pbars.forEach((pbar)->{
                plot.addPoint(pbar.getEndTime().toInstant().toEpochMilli(), pbar.getClosePrice().doubleValue());
            });
        }
        plot.showPlot();
    }
    
    /**
     * @param mainStrategy the mainStrategy to set
     */
    public void setMainStrategy(String mainStrategy) {
        if (signalItem != null) {
            mainStrategy = "Signal";
        }
        strategiesController.setMainStrategy(mainStrategy);
        if (!mainStrategy.equals("Signal")) {
            need_bar_reset = true;
            if (predictor != null) {
                if (predictor.getLearningRule() != null) {
                    predictor.getLearningRule().stopLearning();
                }
                predictor = null;
            }
        }
    }
    
    public void doShowStatistics() {
        strategiesController.logStatistics(false);
    }
    
    public void setCheckOtherStrategies(boolean checkOtherStrategies) {
        this.checkOtherStrategies = checkOtherStrategies;
    }

    @Override
    public SignalItem getSignalItem() {
        return signalItem;
    }
    
    @Override
    public void setSignalItem(SignalItem item) {
        signalItem = item;
    }
    
    /**
     * @return the isTryingToSellOnPeak
     */
    public boolean isTryingToSellOnPeak() {
        return isTryingToSellOnPeak;
    }

    /**
     * @param isTryingToSellOnPeak the isTryingToSellOnPeak to set
     */
    public void setTryingToSellOnPeak(boolean isTryingToSellOnPeak) {
        this.isTryingToSellOnPeak = isTryingToSellOnPeak;
    }

    /**
     * @return the isTryingToBuyDip
     */
    public boolean isTryingToBuyDip() {
        return isTryingToBuyOnDip;
    }

    /**
     * @param isTryingToBuyDip the isTryingToBuyDip to set
     */
    public void setTryingToBuyDip(boolean isTryingToBuyDip) {
        this.isTryingToBuyOnDip = isTryingToBuyDip;
    }
    
    public void setBuyOnStart(boolean buyOnStart) {
        this.buyOnStart = buyOnStart;
    }

    public boolean isBuyOnStart() {
        return buyOnStart;
    }
    
    /**
     * @return the sellOpenOrdersOnPeak
     */
    public boolean isSellOpenOrdersOnPeak() {
        return sellOpenOrdersOnPeak;
    }

    /**
     * @param sellOpenOrdersOnPeak the sellOpenOrdersOnPeak to set
     */
    public void setSellOpenOrdersOnPeak(boolean sellOpenOrdersOnPeak) {
        this.sellOpenOrdersOnPeak = sellOpenOrdersOnPeak;
    }
}
