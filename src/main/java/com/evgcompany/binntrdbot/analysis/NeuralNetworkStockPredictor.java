/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.analysis;

import org.neuroph.core.NeuralNetwork;
import org.neuroph.core.data.DataSet;
import org.neuroph.core.data.DataSetRow;
import org.neuroph.core.events.LearningEvent;
import org.neuroph.core.events.LearningEventListener;
import org.neuroph.core.learning.SupervisedLearning;
import org.neuroph.nnet.ElmanNetwork;
import org.neuroph.nnet.MultiLayerPerceptron;
import org.neuroph.nnet.learning.BackPropagation;
import org.neuroph.nnet.learning.ResilientPropagation;
import org.ta4j.core.TimeSeries;

/**
 *
 * @author EVG_Adminer
 */
public class NeuralNetworkStockPredictor {
    
    private final double learningRate;
    private final int slidingWindowSize;

    private final String neuralNetworkModelFileName = "neuralStockPredictor";
    private final String neuralNetworkModelFileExt = "nnet";
    private String neuralNetworkModelFilePath = "";
    
    private double maxPrice = 0;
    private double minPrice = Double.MAX_VALUE;    
    private double maxVolume = 0;
    private double minVolume = Double.MAX_VALUE;
    
    public NeuralNetworkStockPredictor(String pair, int slidingWindowSize, double rate) {
        this.slidingWindowSize = slidingWindowSize;
        this.learningRate = rate;
        neuralNetworkModelFilePath = neuralNetworkModelFileName+pair+"."+neuralNetworkModelFileExt;
    }
    
    private double normalizePriceValue(double input) {
        return (input - minPrice) / (maxPrice - minPrice) * 0.8 + 0.1;
    }

    private double deNormalizePriceValue(double input) {
        return minPrice + (input - 0.1) * (maxPrice - minPrice) / 0.8;
    }
    
    private double normalizeVolumeValue(double input) {
        return (input - minVolume) / (maxVolume - minVolume) * 0.8 + 0.1;
    }

    private double deNormalizeVolumeValue(double input) {
        return minVolume + (input - 0.1) * (maxVolume - minVolume) / 0.8;
    }
    
    public boolean canLoad() {
        NeuralNetwork neuralNetwork = null;
        try {
            neuralNetwork = NeuralNetwork.createFromFile(neuralNetworkModelFilePath);
        } catch (Exception e) {
            neuralNetwork = null;
        }
        return neuralNetwork != null;
    }
    
    public void trainNetwork(TimeSeries series) {
        NeuralNetwork<BackPropagation> neuralNetwork = new ElmanNetwork(slidingWindowSize * 2, slidingWindowSize * 2, slidingWindowSize * 2 + 1, 1);

        int maxIterations = 10000000;
        double maxError = 0.0001;

        SupervisedLearning learningRule = neuralNetwork.getLearningRule();
        learningRule.setMaxError(maxError);
        learningRule.setLearningRate(learningRate);
        learningRule.setMaxIterations(maxIterations);
        learningRule.addListener(new LearningEventListener() {
            @Override
            public void handleLearningEvent(LearningEvent learningEvent) {
                SupervisedLearning rule = (SupervisedLearning) learningEvent.getSource();
                System.out.println("Network error for iteration " + rule.getCurrentIteration() + ": " + rule.getTotalNetworkError());
            }
        });

        DataSet trainingSet = loadTrainingData(series);
        neuralNetwork.learn(trainingSet);
        neuralNetwork.save(neuralNetworkModelFilePath);
    }
    
    public DataSet loadTrainingData(TimeSeries series) {
        initMinMax(series);
        DataSet trainingSet = new DataSet(slidingWindowSize*2, 1);
        double trainValues[] = new double[slidingWindowSize*2];
        for(int i=series.getBeginIndex() + slidingWindowSize; i < series.getEndIndex(); i++) {
            for(int j = 0; j < slidingWindowSize; j++) {
                trainValues[j*2] = normalizePriceValue(series.getBar(i-slidingWindowSize+j).getClosePrice().doubleValue());
                trainValues[j*2+1] = normalizeVolumeValue(series.getBar(i-slidingWindowSize+j).getVolume().doubleValue());
            }
            double expectedValue[] = new double[]{series.getBar(i).getClosePrice().doubleValue()};
            trainingSet.addRow(new DataSetRow(trainValues, expectedValue));
        }
        return trainingSet;
    }
    
    public double predictNextPrice(TimeSeries series) {
        NeuralNetwork neuralNetwork = NeuralNetwork.createFromFile(neuralNetworkModelFilePath);
        double[] inputs = new double[slidingWindowSize*2];
        int k = 0;
        for(int i=Math.max(series.getEndIndex() - slidingWindowSize, 0); i < series.getEndIndex(); i++) {
            inputs[k*2] = series.getBar(i).getClosePrice().doubleValue();
            inputs[k*2+1] = series.getBar(i).getVolume().doubleValue();
            k++;
        }
        neuralNetwork.setInput(inputs);
        neuralNetwork.calculate();
        double[] networkOutput = neuralNetwork.getOutput();
        return deNormalizePriceValue(networkOutput[0]);
    }
    
    public void initMinMax(TimeSeries series) {
        for(int i=series.getBeginIndex(); i < series.getEndIndex(); i++) {
            if (minPrice > series.getBar(i).getClosePrice().doubleValue()) {
                minPrice = series.getBar(i).getClosePrice().doubleValue();
            }
            if (maxPrice < series.getBar(i).getClosePrice().doubleValue()) {
                maxPrice = series.getBar(i).getClosePrice().doubleValue();
            }
            if (minVolume > series.getBar(i).getVolume().doubleValue()) {
                minVolume = series.getBar(i).getVolume().doubleValue();
            }
            if (maxVolume < series.getBar(i).getVolume().doubleValue()) {
                maxVolume = series.getBar(i).getVolume().doubleValue();
            }
        }
    }
}
