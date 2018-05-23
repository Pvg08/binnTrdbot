/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import com.evgcompany.binntrdbot.api.TradingAPIAbstractInterface;
import com.evgcompany.binntrdbot.misc.CurrencyPlot;
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
    
    private String lastOrderMarker = "";
    
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
        StrategiesController.StrategiesAction saction = strategiesController.checkStatus((pyramidSize != 0 || !longModeAuto), 
            checkOtherStrategies,
            currentPrice.doubleValue()
        );
        if (saction == StrategiesController.StrategiesAction.DO_ENTER && pyramidSize < pyramidAutoMaxSize) {
            if (canBuyForCoinRating()) doEnter(lastStrategyCheckPrice, false);
        } else if (saction == StrategiesController.StrategiesAction.DO_EXIT && pyramidSize > -pyramidAutoMaxSize) {
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
        for(int i=0; i<strategiesController.getStrategyMarkers().size(); i++) {
            plot.addMarker(
                strategiesController.getStrategyMarkers().get(i).label, 
                strategiesController.getStrategyMarkers().get(i).timeStamp, 
                strategiesController.getStrategyMarkers().get(i).typeIndex
            );
            if (strategiesController.getStrategyMarkers().get(i).value > 0) {
                plot.addPoint(strategiesController.getStrategyMarkers().get(i).timeStamp, strategiesController.getStrategyMarkers().get(i).value);
            }
        }
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
