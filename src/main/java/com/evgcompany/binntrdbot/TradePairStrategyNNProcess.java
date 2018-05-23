/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import com.evgcompany.binntrdbot.analysis.NeuralNetworkStockPredictor;
import com.evgcompany.binntrdbot.api.TradingAPIAbstractInterface;
import com.evgcompany.binntrdbot.misc.CurrencyPlot;
import com.evgcompany.binntrdbot.misc.NumberFormatter;
import com.evgcompany.binntrdbot.strategies.core.StrategyItem;
import java.util.List;
import org.ta4j.core.Bar;

/**
 *
 * @author EVG_adm_T
 */
public class TradePairStrategyNNProcess extends TradePairSignalProcess {

    protected NeuralNetworkStockPredictor predictor = null;
    
    public TradePairStrategyNNProcess(TradingAPIAbstractInterface client, String pair) {
        super(client, pair);
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
                    }
                    break;
                default:
                    break;
            }
        }
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
        if (predictor != null && predictor.isHaveNetworkInFile()) {
            List<Bar> pbars = predictor.predictPreviousBars(series);
            pbars.forEach((pbar)->{
                plot.addPoint(pbar.getEndTime().toInstant().toEpochMilli(), pbar.getClosePrice().doubleValue());
            });
        }
        plot.showPlot();
    }
    
    @Override
    public void setMainStrategy(String mainStrategy) {
        super.setMainStrategy(mainStrategy);
        if (predictor != null) {
            if (predictor.getLearningRule() != null) {
                predictor.getLearningRule().stopLearning();
            }
            predictor = null;
        }
    }
}
