/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.analysis;

import com.evgcompany.binntrdbot.mainApplication;
import org.neuroph.core.NeuralNetwork;
import org.neuroph.core.data.DataSet;
import org.neuroph.core.data.DataSetRow;
import org.neuroph.core.events.LearningEvent;
import org.neuroph.core.events.LearningEventListener;
import org.neuroph.core.learning.SupervisedLearning;
import org.neuroph.nnet.ElmanNetwork;
import org.neuroph.nnet.learning.BackPropagation;
import org.ta4j.core.TimeSeries;

/**
 *
 * @author EVG_Adminer
 */
public class NeuralNetworkStockPredictor extends Thread {
   
    private double learningRate = 1;
    private final int slidingWindowSize;

    private final String neuralNetworkFileName = "neuralStockPredictor";
    private final String neuralNetworkModelFileExt = "nnet";
    private final String neuralNetworkDataFileExt = "dset";
    private String neuralNetworkModelFilePath = "";
    private String neuralNetworkDataFilePath = "";
    
    private double maxPrice = 0;
    private double minPrice = Double.MAX_VALUE;    
    private double maxVolume = 0;
    private double minVolume = Double.MAX_VALUE;
    private boolean saveTrainData = false;
    
    private TimeSeries run_series = null;
    private SupervisedLearning learningRule = null;
    private boolean learning = false;
    private String stock_name = "";
    
    private boolean have_network_in_file = false;
    private boolean have_dataset_in_file = false;
    
    private boolean skipLastBar = true;
    
    public NeuralNetworkStockPredictor(String name, int slidingWindowSize) {
        this.stock_name = name;
        this.slidingWindowSize = slidingWindowSize;
        neuralNetworkModelFilePath = neuralNetworkFileName+"_"+name+"_"+slidingWindowSize+"."+neuralNetworkModelFileExt;
        neuralNetworkDataFilePath = neuralNetworkFileName+"_"+name+"_"+slidingWindowSize+"."+neuralNetworkDataFileExt;
        have_network_in_file = canLoadNetwork();
        have_dataset_in_file = canLoadDataset();
    }

    private void doWait(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
        }
    }
    
    public void initTrainNetwork(TimeSeries series) {
        run_series = series;
    }
    
    @Override
    public void run() {
        mainApplication.getInstance().log("Neural network "+stock_name+" begin learning...");
        learning = true;
        trainNetwork(run_series);
        run_series = null;
        learning = false;
        mainApplication.getInstance().log("Neural network "+stock_name+" learning complete! Error = " + learningRule.getTotalNetworkError());
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
    
    private boolean canLoadNetwork() {
        NeuralNetwork neuralNetwork = null;
        try {
            neuralNetwork = NeuralNetwork.createFromFile(neuralNetworkModelFilePath);
        } catch (Exception e) {
            neuralNetwork = null;
        }
        return neuralNetwork != null;
    }
    private boolean canLoadDataset() {
        DataSet trset = null;
        try {
            trset = DataSet.createFromFile(neuralNetworkDataFilePath, slidingWindowSize*2, 1, "\t", true);
        } catch (Exception e) {
            trset = null;
        }
        return trset != null;
    }
    
    public void trainNetwork(TimeSeries series) {
        NeuralNetwork<BackPropagation> neuralNetwork = new ElmanNetwork(slidingWindowSize * 2, slidingWindowSize * 6, slidingWindowSize * 6 + 1, 1);

        int maxIterations = 8;
        double maxError = 0.0001;

        learningRule = neuralNetwork.getLearningRule();
        getLearningRule().setMaxError(maxError);
        getLearningRule().setLearningRate(getLearningRate());
        getLearningRule().setMaxIterations(maxIterations);
        getLearningRule().addListener(new LearningEventListener() {
            @Override
            public void handleLearningEvent(LearningEvent learningEvent) {
                SupervisedLearning rule = (SupervisedLearning) learningEvent.getSource();
                mainApplication.getInstance().log("Network "+stock_name+" iteration " + rule.getCurrentIteration() + ": error = " + rule.getTotalNetworkError());
            }
        });

        DataSet trainingSet;
        if (series == null && have_dataset_in_file) {
            trainingSet = DataSet.createFromFile(neuralNetworkDataFilePath, slidingWindowSize*2, 1, "\t", true);
        } else {
            trainingSet = loadTrainingData(series);
            if (saveTrainData) {
                trainingSet.saveAsTxt(neuralNetworkDataFilePath, "\t");
                have_dataset_in_file = true;
            }
        }
        neuralNetwork.learn(trainingSet);
        neuralNetwork.save(neuralNetworkModelFilePath);
        have_network_in_file = true;
    }
    
    public DataSet loadTrainingData(TimeSeries series) {
        initMinMax(series);
        DataSet trainingSet = null;
        try {
            trainingSet = DataSet.createFromFile(neuralNetworkDataFilePath, slidingWindowSize*2, 1, "\t", true);
        } catch(Exception e) {
            trainingSet = new DataSet(slidingWindowSize*2, 1);
        }
        double trainValues[] = new double[slidingWindowSize*2];
        double expectedValue[] = new double[1];
        int endIndex = lastBarIndex(series);
        for(int i=series.getBeginIndex() + slidingWindowSize; i < endIndex; i++) {
            for(int j = 0; j < slidingWindowSize; j++) {
                trainValues[j*2] = normalizePriceValue(series.getBar(i-slidingWindowSize+j).getClosePrice().doubleValue());
                trainValues[j*2+1] = normalizeVolumeValue(series.getBar(i-slidingWindowSize+j).getVolume().doubleValue());
            }
            expectedValue[0] = normalizePriceValue(series.getBar(i).getClosePrice().doubleValue());
            trainingSet.addRow(new DataSetRow(trainValues.clone(), expectedValue.clone()));
        }
        return trainingSet;
    }

    public double predictNextPrice(TimeSeries series) {
        NeuralNetwork neuralNetwork = NeuralNetwork.createFromFile(neuralNetworkModelFilePath);
        double[] inputs = new double[slidingWindowSize*2];
        int k = 0;
        int endIndex = series.getEndIndex();
        for(int i=Math.max(endIndex - slidingWindowSize, 0); i < endIndex; i++) {
            inputs[k*2] = normalizePriceValue(series.getBar(i).getClosePrice().doubleValue());
            inputs[k*2+1] = normalizeVolumeValue(series.getBar(i).getVolume().doubleValue());
            k++;
        }
        neuralNetwork.setInput(inputs);
        neuralNetwork.calculate();
        double[] networkOutput = neuralNetwork.getOutput();
        return deNormalizePriceValue(networkOutput[0]);
    }
    
    public void initMinMax(TimeSeries series) {
        int endIndex = lastBarIndex(series);
        for(int i = series.getBeginIndex(); i < endIndex; i++) {
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

    private int lastBarIndex(TimeSeries series) {
        if (skipLastBar) {
            return series.getEndIndex()-1;
        } else {
            return series.getEndIndex();
        }
    }
    
    /**
     * @return the saveTrainData
     */
    public boolean isSaveTrainData() {
        return saveTrainData;
    }

    /**
     * @param saveTrainData the saveTrainData to set
     */
    public void setSaveTrainData(boolean saveTrainData) {
        this.saveTrainData = saveTrainData;
    }

    /**
     * @return the learningRule
     */
    public SupervisedLearning getLearningRule() {
        return learningRule;
    }

    /**
     * @return the learning
     */
    public boolean isLearning() {
        return learning;
    }

    /**
     * @return the stock_name
     */
    public String getStockName() {
        return stock_name;
    }

    /**
     * @return the learningRate
     */
    public double getLearningRate() {
        return learningRate;
    }

    /**
     * @param learningRate the learningRate to set
     */
    public void setLearningRate(double learningRate) {
        this.learningRate = learningRate;
    }

    /**
     * @return the have_network_in_file
     */
    public boolean isHaveNetworkInFile() {
        return have_network_in_file;
    }

    /**
     * @return the have_dataset_in_file
     */
    public boolean isHaveDatasetInFile() {
        return have_dataset_in_file;
    }

    /**
     * @return the skipLastBar
     */
    public boolean isSkipLastBar() {
        return skipLastBar;
    }

    /**
     * @param skipLastBar the skipLastBar to set
     */
    public void setSkipLastBar(boolean skipLastBar) {
        this.skipLastBar = skipLastBar;
    }
}
