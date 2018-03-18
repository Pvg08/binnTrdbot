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
import org.neuroph.core.learning.SupervisedLearning;
import org.neuroph.nnet.MultiLayerPerceptron;
import org.neuroph.nnet.learning.LMS;
import org.neuroph.util.TransferFunctionType;
import org.ta4j.core.TimeSeries;

/**
 *
 * @author EVG_Adminer
 */
public class NeuralNetworkStockPredictor extends Thread {
   
    private double learningRate = 0.7;
    private final int slidingWindowSize;

    private final String neuralNetworkFileName = "neuralStockPredictor";
    private final String neuralNetworkModelFileExt = "nnet";
    private final String neuralNetworkDataFileExt = "dset";
    private String neuralNetworkModelFilePath = "";
    private String neuralNetworkModelBaseFilePath = "";
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
    private boolean have_network_in_base_file = false;
    private boolean have_dataset_in_file = false;
    
    private boolean skipLastBar = true;
    
    public NeuralNetworkStockPredictor(String name, int slidingWindowSize) {
        this.slidingWindowSize = slidingWindowSize;
        init(name);
    }

    private void init(String name) {
        neuralNetworkModelFilePath = neuralNetworkFileName+"_"+name+"_"+slidingWindowSize+"."+neuralNetworkModelFileExt;
        neuralNetworkModelBaseFilePath = neuralNetworkFileName+"__"+slidingWindowSize+"."+neuralNetworkModelFileExt;
        neuralNetworkDataFilePath = neuralNetworkFileName+"_"+name+"_"+slidingWindowSize+"."+neuralNetworkDataFileExt;
        have_network_in_file = canLoadNetwork(neuralNetworkModelFilePath);
        if (name.isEmpty()) {
            have_network_in_base_file = have_network_in_file;
            this.stock_name = "BASE";
        } else {
            have_network_in_base_file = canLoadNetwork(neuralNetworkModelBaseFilePath);
            this.stock_name = name;
        }
        have_dataset_in_file = canLoadDataset();
    }
    
    public void toBase() {
        init("");
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
        if (run_series != null) {
            initMinMax(run_series);
        }
        run_series = null;
        learning = false;
        mainApplication.getInstance().log("Neural network "+stock_name+" learning complete! Error = " + learningRule.getTotalNetworkError());
    }
    
    private double normalizeValue(double input, double min, double max) {
        return (input - min) / (max - min) * 0.8 + 0.1;
    }

    private double deNormalizeValue(double input, double min, double max) {
        return min + (input - 0.1) * (max - min) / 0.8;
    }
    
    private boolean canLoadNetwork(String _neuralNetworkModelFilePath) {
        NeuralNetwork neuralNetwork = null;
        try {
            neuralNetwork = NeuralNetwork.createFromFile(_neuralNetworkModelFilePath);
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
        NeuralNetwork neuralNetwork = new MultiLayerPerceptron(TransferFunctionType.GAUSSIAN, slidingWindowSize * 2, slidingWindowSize * 54, slidingWindowSize * 32 + 1, 1);

        int maxIterations = 50;
        double maxError = 0.000001;

        learningRule = (LMS) neuralNetwork.getLearningRule();
        learningRule.setMaxError(maxError);
        learningRule.setLearningRate(getLearningRate());
        learningRule.setMaxIterations(maxIterations);
        learningRule.setLearningRate(learningRate);
        learningRule.addListener((LearningEvent learningEvent) -> {
            SupervisedLearning rule = (SupervisedLearning) learningEvent.getSource();
            mainApplication.getInstance().log("Network "+stock_name+" iteration " + rule.getCurrentIteration() + ": error = " + rule.getTotalNetworkError());
        });

        DataSet trainingSet;
        if (series == null && have_dataset_in_file) {
            trainingSet = DataSet.createFromFile(neuralNetworkDataFilePath, slidingWindowSize*2, 1, "\t", true);
        } else {
            trainingSet = appendTrainingData(series);
        }
        
        mainApplication.getInstance().log("Dataset size: "+trainingSet.getRows().size());
        mainApplication.getInstance().log("Network input size: "+trainingSet.getInputSize());
        mainApplication.getInstance().log("Network output size: "+trainingSet.getOutputSize());
        
        neuralNetwork.learn(trainingSet);
        neuralNetwork.save(neuralNetworkModelFilePath);
        have_network_in_file = true;
    }
    
    public DataSet appendTrainingData(TimeSeries series) {
        DataSet trainingSet = loadTrainingData(series);
        if (saveTrainData) {
            trainingSet.saveAsTxt(neuralNetworkDataFilePath, "\t");
            have_dataset_in_file = true;
        }
        return trainingSet;
    }
    
    public DataSet loadTrainingData(TimeSeries series) {
        return loadTrainingData(series, series.getBeginIndex(), lastBarIndex(series), null);
    }

    private DataSet loadTrainingData(TimeSeries series, int index_from, int index_to, DataSet trainingSet) {
        if ((index_to - index_from) < 2) {
            return trainingSet;
        }
        if (trainingSet == null) {
            initMinMax(series);
            try {
                trainingSet = DataSet.createFromFile(neuralNetworkDataFilePath, slidingWindowSize*2, 1, "\t", true);
            } catch(Exception e) {
                trainingSet = new DataSet(slidingWindowSize*2, 1);
            }
        }
        
        double maxPriceLocal = 0;
        double minPriceLocal = Double.MAX_VALUE;    
        double maxVolumeLocal = 0;
        double minVolumeLocal = Double.MAX_VALUE;
        
        for(int i=index_from; i < index_to; i++) {
            if (minPriceLocal > series.getBar(i).getClosePrice().doubleValue()) {
                minPriceLocal = series.getBar(i).getClosePrice().doubleValue();
            }
            if (maxPriceLocal < series.getBar(i).getClosePrice().doubleValue()) {
                maxPriceLocal = series.getBar(i).getClosePrice().doubleValue();
            }
            if (minVolumeLocal > series.getBar(i).getVolume().doubleValue()) {
                minVolumeLocal = series.getBar(i).getVolume().doubleValue();
            }
            if (maxVolumeLocal < series.getBar(i).getVolume().doubleValue()) {
                maxVolumeLocal = series.getBar(i).getVolume().doubleValue();
            }
        }
        
        double trainValues[] = new double[slidingWindowSize*2];
        double expectedValue[] = new double[1];
        for(int i=index_from + slidingWindowSize; i < index_to; i++) {
            for(int j = 0; j < slidingWindowSize; j++) {
                trainValues[j*2] = normalizeValue(series.getBar(i-slidingWindowSize+j).getClosePrice().doubleValue(), minPriceLocal, maxPriceLocal);
                trainValues[j*2+1] = normalizeValue(series.getBar(i-slidingWindowSize+j).getVolume().doubleValue(), minVolumeLocal, maxVolumeLocal);
            }
            expectedValue[0] = normalizeValue(series.getBar(i).getClosePrice().doubleValue(), minPriceLocal, maxPriceLocal);
            trainingSet.addRow(new DataSetRow(trainValues.clone(), expectedValue.clone()));
            loadTrainingData(series, i-slidingWindowSize, i, trainingSet);
        }
        return trainingSet;
    }

    public double predictNextPrice(TimeSeries series) {
        NeuralNetwork neuralNetwork = NeuralNetwork.createFromFile(neuralNetworkModelFilePath);
        double[] inputs = new double[slidingWindowSize*2];
        int k = 0;
        int endIndex = series.getEndIndex();
        for(int i=Math.max(endIndex - slidingWindowSize, 0); i < endIndex; i++) {
            inputs[k*2] = normalizeValue(series.getBar(i).getClosePrice().doubleValue(), minPrice, maxPrice);
            inputs[k*2+1] = normalizeValue(series.getBar(i).getVolume().doubleValue(), minVolume, maxVolume);
            k++;
        }
        neuralNetwork.setInput(inputs);
        neuralNetwork.calculate();
        double[] networkOutput = neuralNetwork.getOutput();
        return deNormalizeValue(networkOutput[0], minPrice, maxPrice);
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
     * @return the have_network_in_base_file
     */
    public boolean isHaveNetworkInBaseFile() {
        return have_network_in_base_file;
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
