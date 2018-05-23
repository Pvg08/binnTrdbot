/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import com.evgcompany.binntrdbot.api.TradingAPIAbstractInterface;
import com.evgcompany.binntrdbot.misc.CurrencyPlot;
import com.evgcompany.binntrdbot.strategies.core.OrderActionType;
import com.evgcompany.binntrdbot.strategies.core.StrategiesController;
import com.evgcompany.binntrdbot.strategies.core.StrategyItem;
import java.math.BigDecimal;
import org.ta4j.core.Decimal;

/**
 *
 * @author EVG_adm_T
 */
public class TradePairStrategyProcess extends AbstractTradePairProcess {

    protected StrategiesController strategiesController = null;
    
    protected boolean checkOtherStrategies = true;
    
    public TradePairStrategyProcess(TradingAPIAbstractInterface client, String pair) {
        super(client, pair);
        strategiesController = new StrategiesController(symbol);
    }

    protected boolean canBuyForCoinRating() {
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
        OrderActionType saction = strategiesController.checkStatus((pyramidSize != 0 || !longModeAuto), 
            checkOtherStrategies,
            currentPrice.doubleValue()
        );
        if (saction == OrderActionType.DO_STRATEGY_ENTER && pyramidSize < pyramidAutoMaxSize) {
            if (canBuyForCoinRating()) doEnter(lastStrategyCheckPrice, false);
        } else if (saction == OrderActionType.DO_STRATEGY_EXIT && pyramidSize > -pyramidAutoMaxSize) {
            doExit(lastStrategyCheckPrice, false);
        }
        super.checkStatus();
    }
    
    @Override
    protected void setNewSeries() {
        series = strategiesController.resetSeries();
    }
    
    protected void beforeResetStrategies() {
        // nothing
    }
    
    @Override
    protected void resetBars() {
        super.resetBars();
        beforeResetStrategies();
        strategiesController.resetStrategies();
    }
    
    @Override
    protected void runStart() {
        super.runStart();
        beforeResetStrategies();
        strategiesController.resetStrategies();
    }
    
    @Override
    public void doShowPlot() {
        StrategyItem item = strategiesController.getMainStrategyItem();
        plot = new CurrencyPlot(symbol, series, strategiesController.getTradingRecord(), item != null ? item.getInitializer() : null);
        strategiesController.getStrategyMarkers().forEach((marker) -> {
            plot.addMarker(marker);
        });
        markers.forEach((marker) -> {
            plot.addMarker(marker);
        });
        plot.showPlot();
    }
    
    /**
     * @param mainStrategy the mainStrategy to set
     */
    public void setMainStrategy(String mainStrategy) {
        strategiesController.setMainStrategy(mainStrategy);
        need_bar_reset = true;
    }
    
    @Override
    public void doShowStatistics() {
        super.doShowStatistics();
        strategiesController.logStatistics(false);
    }
    
    public void setCheckOtherStrategies(boolean checkOtherStrategies) {
        this.checkOtherStrategies = checkOtherStrategies;
    }
}
