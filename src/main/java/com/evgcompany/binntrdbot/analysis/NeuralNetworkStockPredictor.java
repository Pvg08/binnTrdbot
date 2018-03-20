/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.analysis;

import com.evgcompany.binntrdbot.mainApplication;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.neuroph.core.NeuralNetwork;
import org.neuroph.core.data.DataSet;
import org.neuroph.core.data.DataSetRow;
import org.neuroph.core.events.LearningEvent;
import org.neuroph.core.learning.SupervisedLearning;
import org.neuroph.nnet.MultiLayerPerceptron;
import org.neuroph.nnet.learning.LMS;
import org.neuroph.util.TransferFunctionType;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.Decimal;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuChikouSpanIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuKijunSenIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanAIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanBIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuTenkanSenIndicator;
import org.ta4j.core.indicators.volume.ChaikinMoneyFlowIndicator;

/**
 *
 * @author EVG_Adminer
 */
public class NeuralNetworkStockPredictor extends Thread {
    private double learningRate = 0.7;
    private int slidingWindowSize = 5;
    private int inputVectorPartSize = 10;
    private int inputVectorSize = slidingWindowSize * inputVectorPartSize;

    private final String neuralNetworkFileName = "neuralStockPredictor";
    private final String neuralNetworkModelFileExt = "nnet";
    private final String neuralNetworkDataFileExt = "dset";
    private String neuralNetworkModelFilePath = "";
    private String neuralNetworkModelBaseFilePath = "";
    private String neuralNetworkDataFilePath = "";
    
    private double[] maxVector = null;
    private double[] minVector = null;
    
    private double maxPrice = 0;
    private double minPrice = Double.MAX_VALUE;
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
        inputVectorSize = slidingWindowSize * inputVectorPartSize;
        init(name);
    }
    public NeuralNetworkStockPredictor(String name) {
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
            trset = DataSet.createFromFile(neuralNetworkDataFilePath, inputVectorSize, 1, "\t", true);
        } catch (Exception e) {
            trset = null;
        }
        return trset != null;
    }

    private double[] getInputForSeries(TimeSeries series, int begin_index, boolean normalize) {
        
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        IchimokuTenkanSenIndicator tenkanSen = new IchimokuTenkanSenIndicator(series, 3);
        IchimokuKijunSenIndicator kijunSen = new IchimokuKijunSenIndicator(series, 5);
        IchimokuSenkouSpanAIndicator senkouSpanA = new IchimokuSenkouSpanAIndicator(series, tenkanSen, kijunSen);
        IchimokuSenkouSpanBIndicator senkouSpanB = new IchimokuSenkouSpanBIndicator(series, 9);
        IchimokuChikouSpanIndicator chikouSpan = new IchimokuChikouSpanIndicator(series, 5);
        
        EMAIndicator shortEma = new EMAIndicator(closePrice, 5);
        EMAIndicator longEma = new EMAIndicator(closePrice, 20);
        RSIIndicator rsi = new RSIIndicator(closePrice, 2);
        
        ChaikinMoneyFlowIndicator cmf = new ChaikinMoneyFlowIndicator(series, 7);
        
        int end_index = begin_index + slidingWindowSize;
        double trainValues[] = new double[inputVectorSize];
        int k = 0, i;
        for(i = begin_index; i < end_index; i++) {
            trainValues[k * 10] = closePrice.getValue(i).doubleValue();
            trainValues[k * 10 + 1] = tenkanSen.getValue(i).doubleValue();
            trainValues[k * 10 + 2] = kijunSen.getValue(i).doubleValue();
            trainValues[k * 10 + 3] = senkouSpanA.getValue(i).doubleValue();
            trainValues[k * 10 + 4] = senkouSpanB.getValue(i).doubleValue();
            trainValues[k * 10 + 5] = chikouSpan.getValue(i).doubleValue();
            trainValues[k * 10 + 6] = shortEma.getValue(i).doubleValue();
            trainValues[k * 10 + 7] = longEma.getValue(i).doubleValue();
            trainValues[k * 10 + 8] = rsi.getValue(i).doubleValue();
            trainValues[k * 10 + 9] = cmf.getValue(i).doubleValue();
            k++;
        }
        for(k=0; k<inputVectorSize; k++) {
            if (Double.isNaN(trainValues[k])) {
                trainValues[k] = 0.0;
            } else if (Double.isInfinite(trainValues[k])) {
                trainValues[k] = Math.signum(trainValues[k]);
            }
        }
        
        if (normalize) {
            for(int j=0; j<slidingWindowSize; j++) {
                for(int n=0; n<inputVectorPartSize; n++) {
                    trainValues[j*inputVectorPartSize + n] = this.normalizeValue(trainValues[j*inputVectorPartSize + n], minVector[n], maxVector[n]);
                }
            }
        }
        
        return trainValues;
    }
    
    public void trainNetwork(TimeSeries series) {
        NeuralNetwork neuralNetwork = new MultiLayerPerceptron(TransferFunctionType.SIGMOID, inputVectorSize, inputVectorSize * 32, inputVectorSize * 16, inputVectorSize * 4, 1);

        int maxIterations = 15;
        double maxError = 0.0000001;

        learningRule = (LMS) neuralNetwork.getLearningRule();
        learningRule.setMaxError(maxError);
        learningRule.setLearningRate(getLearningRate());
        learningRule.setMaxIterations(maxIterations);
        learningRule.setLearningRate(learningRate);
        learningRule.setMinErrorChange(0.0000000001);
        learningRule.setMinErrorChangeIterationsLimit(10);
        learningRule.addListener((LearningEvent learningEvent) -> {
            SupervisedLearning rule = (SupervisedLearning) learningEvent.getSource();
            mainApplication.getInstance().log("Network "+stock_name+" iteration " + rule.getCurrentIteration() + ": error = " + rule.getTotalNetworkError());
        });

        DataSet trainingSet;
        if (series == null && have_dataset_in_file) {
            trainingSet = DataSet.createFromFile(neuralNetworkDataFilePath, inputVectorSize, 1, "\t", true);
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
                trainingSet = DataSet.createFromFile(neuralNetworkDataFilePath, inputVectorSize, 1, "\t", true);
            } catch(Exception e) {
                trainingSet = new DataSet(inputVectorSize, 1);
            }
        }
        
        double maxPriceLocal = Double.MIN_VALUE;
        double minPriceLocal = Double.MAX_VALUE;
        
        for(int i=index_from; i < index_to; i++) {
            if (minPriceLocal > series.getBar(i).getClosePrice().doubleValue()) {
                minPriceLocal = series.getBar(i).getClosePrice().doubleValue();
            }
            if (maxPriceLocal < series.getBar(i).getClosePrice().doubleValue()) {
                maxPriceLocal = series.getBar(i).getClosePrice().doubleValue();
            }
        }
        
        for(int i=index_from + slidingWindowSize; i < index_to; i++) {
            double trainValues[] = getInputForSeries(series, i-slidingWindowSize, true);
            double expectedValue[] = new double[1];
            expectedValue[0] = normalizeValue(series.getBar(i).getClosePrice().doubleValue(), minPriceLocal, maxPriceLocal);
            trainingSet.addRow(new DataSetRow(trainValues, expectedValue));
            loadTrainingData(series, i-slidingWindowSize, i, trainingSet);
        }
        return trainingSet;
    }

    private Bar getPredictedBar(Bar lastbar, double predicted_close_price) {
        double ncprice = deNormalizeValue(predicted_close_price, minPrice, maxPrice);
        double oprice = lastbar.getClosePrice().doubleValue();
        Bar bar = new BaseBar(
            lastbar.getTimePeriod(), 
            lastbar.getEndTime().plusSeconds(lastbar.getTimePeriod().getSeconds()),
            Decimal.valueOf(oprice),
            Decimal.valueOf(Math.max(oprice, ncprice)),
            Decimal.valueOf(Math.min(oprice, ncprice)),
            Decimal.valueOf(ncprice),
            Decimal.ZERO
        );
        return bar;
    }
    
    public Bar predictNextBar(TimeSeries series) {
        NeuralNetwork neuralNetwork = NeuralNetwork.createFromFile(neuralNetworkModelFilePath);
        int endIndex = series.getEndIndex();
        neuralNetwork.setInput(getInputForSeries(series, Math.max(endIndex - slidingWindowSize + 1, 0), true));
        neuralNetwork.calculate();
        double[] networkOutput = neuralNetwork.getOutput();
        return getPredictedBar(series.getBar(endIndex), networkOutput[0]);
    }
    
    public List<Bar> predictPreviousBars(TimeSeries series) {
        List<Bar> results = new ArrayList<>(0);
        NeuralNetwork neuralNetwork = NeuralNetwork.createFromFile(neuralNetworkModelFilePath);
        int startIndex = Math.min(series.getBeginIndex() + slidingWindowSize, series.getEndIndex());
        int finishIndex = Math.max(series.getEndIndex() - slidingWindowSize, series.getBeginIndex());
        
        for(int j = startIndex; j <= finishIndex; j++) {
            neuralNetwork.setInput(getInputForSeries(series, j, true));
            neuralNetwork.calculate();
            double[] networkOutput = neuralNetwork.getOutput();
            results.add(getPredictedBar(series.getBar(j+slidingWindowSize-1), networkOutput[0]));
        }
        return results;
    }
    
    public void initMinMax(TimeSeries series) {
        if (maxVector == null) {
            maxVector = new double[inputVectorPartSize];
            for(int i=0; i<maxVector.length; i++) maxVector[i] = Double.MIN_VALUE;
        }
        if (minVector == null) {
            minVector = new double[inputVectorPartSize];
            for(int i=0; i<minVector.length; i++) minVector[i] = Double.MAX_VALUE;
        }
        int endIndex = lastBarIndex(series);
        for(int i = series.getBeginIndex(); i < endIndex; i++) {
            if (minPrice > series.getBar(i).getClosePrice().doubleValue()) {
                minPrice = series.getBar(i).getClosePrice().doubleValue();
            }
            if (maxPrice < series.getBar(i).getClosePrice().doubleValue()) {
                maxPrice = series.getBar(i).getClosePrice().doubleValue();
            }
        }
        for(int i = series.getBeginIndex(); i < endIndex - slidingWindowSize; i++) {
            double[] inputs = getInputForSeries(series, i, false);
            
            if (i == series.getBeginIndex())
            System.out.println("INPUT: " + Arrays.toString(inputs));
            
            for(int j=0; j<slidingWindowSize; j++) {
                for(int k=0; k<inputVectorPartSize; k++) {
                    if (minVector[k] > inputs[j*inputVectorPartSize + k]) {
                        minVector[k] = inputs[j*inputVectorPartSize + k];
                    }
                    if (maxVector[k] < inputs[j*inputVectorPartSize + k]) {
                        maxVector[k] = inputs[j*inputVectorPartSize + k];
                    }
                }
            }
            
            
        }
        
        System.out.println("MIN: " + Arrays.toString(minVector));
        System.out.println("MAX: " + Arrays.toString(maxVector));
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
