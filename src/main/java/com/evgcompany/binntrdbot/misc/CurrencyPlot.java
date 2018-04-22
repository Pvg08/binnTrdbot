/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.misc;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Paint;
import java.awt.event.WindowEvent;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.Date;
import javax.swing.JFrame;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.DatasetRenderingOrder;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.CandlestickRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.DefaultHighLowDataset;
import org.jfree.data.xy.OHLCDataset;
import org.jfree.data.xy.XYDataset;
import org.ta4j.core.Bar;
import org.ta4j.core.TimeSeries;
import com.evgcompany.binntrdbot.analysis.StrategyDatasetInitializer;
import org.ta4j.core.TradingRecord;

/**
 *
 * @author EVG_Adminer
 */
public class CurrencyPlot extends JFrame {
    private TimeSeries tseries = null;
    private TradingRecord tradingRecord = null;
    private String stockSymbol;
    
    private final XYPlot mainPlot;
    private final org.jfree.data.time.TimeSeries points;
    
    private double maxVolume;
    private StrategyDatasetInitializer datasetInitializer = null;
    
    /**
     * Builds a JFreeChart OHLC dataset from a ta4j time series.
     * @param series a time series
     * @return an Open-High-Low-Close dataset
     */
    private OHLCDataset createOHLCDataset(TimeSeries series) {
        maxVolume = 0;
        final int nbBars = series.getBarCount();
        Date[] dates = new Date[nbBars];
        double[] opens = new double[nbBars];
        double[] highs = new double[nbBars];
        double[] lows = new double[nbBars];
        double[] closes = new double[nbBars];
        double[] volumes = new double[nbBars];
        for (int i = 0; i < nbBars; i++) {
            Bar bar = series.getBar(i);
            dates[i] = new Date(bar.getEndTime().toEpochSecond() * 1000);
            opens[i] = bar.getOpenPrice().doubleValue();
            highs[i] = bar.getMaxPrice().doubleValue();
            lows[i] = bar.getMinPrice().doubleValue();
            closes[i] = bar.getClosePrice().doubleValue();
            volumes[i] = bar.getVolume().doubleValue();
            if (maxVolume < volumes[i]) {
                maxVolume = volumes[i];
            }
        }
        return new DefaultHighLowDataset(stockSymbol, dates, highs, lows, opens, closes, volumes);
    }

    /**
     * Builds an additional JFreeChart dataset from a ta4j time series.
     * @param series a time series
     * @return an additional dataset
     */
    private TimeSeriesCollection createAdditionalDataset(TimeSeries series) {
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        if (datasetInitializer != null) {
            datasetInitializer.onInit(series, tradingRecord, dataset);
        }
        return dataset;
    }

    /**
     * Displays a chart in a frame.
     * @param chart the chart to be displayed
     */
    private void displayChart(JFreeChart chart) {
        // Chart panel
        ChartPanel panel = new ChartPanel(chart);
        panel.setFillZoomRectangle(true);
        panel.setMouseWheelEnabled(true);
        panel.setPreferredSize(new java.awt.Dimension(1024, 500));
        setContentPane(panel);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }
    
    public CurrencyPlot(String _stockSymbol, TimeSeries _tseries, TradingRecord _tradingRecord, StrategyDatasetInitializer datasetInitializer) {
        super(_stockSymbol + " candlestick");
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        tseries = _tseries;
        tradingRecord = _tradingRecord;
        stockSymbol = _stockSymbol;
        this.datasetInitializer = datasetInitializer;

        /*
          Creating the OHLC dataset
         */
        OHLCDataset ohlcDataset = createOHLCDataset(tseries);

        /*
          Creating the additional dataset
         */
        TimeSeriesCollection xyDataset = createAdditionalDataset(tseries);
        TimeSeriesCollection pointsDataset = new TimeSeriesCollection();
        
        /*
          Creating the chart
         */
        JFreeChart chart = ChartFactory.createCandlestickChart(
                stockSymbol,
                "Time",
                "Price",
                ohlcDataset,
                true);
        // Candlestick rendering
        CandlestickRenderer renderer = new CandlestickRenderer() {
            @Override
            public Paint getItemPaint(int row, int column) {
                XYDataset dataset = getPlot().getDataset();
                OHLCDataset highLowData = (OHLCDataset) dataset;
                int series = row, item = column;
                Number yOpen = highLowData.getOpen(series, item);
                Number yClose = highLowData.getClose(series, item);
                boolean isUpCandle = yClose.doubleValue() > yOpen.doubleValue();
                if (isUpCandle) {
                    return getUpPaint();
                }
                else {
                    return getDownPaint();
                }
            }
        };
        renderer.setAutoWidthMethod(CandlestickRenderer.WIDTHMETHOD_SMALLEST);
        renderer.setUseOutlinePaint(false);
        renderer.setUpPaint(Color.GREEN);
        renderer.setDownPaint(Color.RED);
        mainPlot = chart.getXYPlot();
        mainPlot.setRenderer(renderer);

        // Additional dataset
        mainPlot.setDataset(1, xyDataset);
        mainPlot.mapDatasetToRangeAxis(1, 0);
        XYLineAndShapeRenderer renderer2 = new XYLineAndShapeRenderer(true, false);
        renderer2.setSeriesPaint(0, Color.BLUE);
        renderer2.setSeriesPaint(1, Color.MAGENTA);
        renderer2.setSeriesPaint(2, Color.ORANGE);
        renderer2.setSeriesPaint(3, Color.CYAN);
        renderer2.setSeriesPaint(4, Color.decode("#217300"));
        renderer2.setSeriesPaint(5, Color.decode("#852cbb"));
        renderer2.setSeriesPaint(6, Color.decode("#a09dff"));
        renderer2.setSeriesStroke(0, new BasicStroke(2));
        renderer2.setSeriesStroke(1, new BasicStroke(2));
        renderer2.setSeriesStroke(2, new BasicStroke(2));
        renderer2.setSeriesStroke(3, new BasicStroke(2));
        renderer2.setSeriesStroke(4, new BasicStroke(2));
        renderer2.setSeriesStroke(5, new BasicStroke(2));
        renderer2.setSeriesStroke(6, new BasicStroke(2));
        mainPlot.setRenderer(1, renderer2);

        points = new org.jfree.data.time.TimeSeries("Points");
        pointsDataset.addSeries(points);
        mainPlot.setDataset(2, pointsDataset);
        mainPlot.mapDatasetToRangeAxis(2, 0);
        XYLineAndShapeRenderer renderer3 = new XYLineAndShapeRenderer(false, true);
        renderer3.setSeriesPaint(0, Color.DARK_GRAY);
        mainPlot.setRenderer(2, renderer3);
        
        // Misc
        mainPlot.setRangeGridlinePaint(Color.lightGray);
        mainPlot.setBackgroundPaint(Color.white);
        NumberAxis numberAxis = (NumberAxis) mainPlot.getRangeAxis();
        numberAxis.setAutoRange(true);
        numberAxis.setUpperMargin(0.0D);
        numberAxis.setLowerMargin(0.0D);
        numberAxis.setAutoRangeIncludesZero(false);
        numberAxis.setNumberFormatOverride(new DecimalFormat("0.########"));
        NumberAxis  rangeAxis2 = new NumberAxis("Volume");
        mainPlot.setRangeAxis( 1, rangeAxis2);
        rangeAxis2.setUpperMargin(maxVolume);

        mainPlot.setDatasetRenderingOrder(DatasetRenderingOrder.FORWARD);
        
        /*
          Displaying the chart
         */
        displayChart(chart);
    }
    
    public void updateSeries() {
        if (tseries != null) {
            /*
              Creating the OHLC dataset
            */
            OHLCDataset ohlcDataset = createOHLCDataset(tseries);
            /*
              Creating the additional dataset
            */
            TimeSeriesCollection xyDataset = createAdditionalDataset(tseries);
            mainPlot.setDataset(ohlcDataset);
            mainPlot.setDataset(1, xyDataset);
        }
    }
    
    public void addMarker(String label, double value, int typeIndex) {
        ValueMarker marker = new ValueMarker(value);
        marker.setLabel(label);
        if (typeIndex < 2) {
            marker.setStroke(new BasicStroke(2));
            marker.setPaint(typeIndex==0 ? Color.ORANGE : Color.MAGENTA);
        } else {
            marker.setStroke(new BasicStroke(1));
            marker.setPaint(typeIndex==2 ? new Color(255, 189, 145) : new Color(255, 140, 255));
        }
        marker.setAlpha(0.15f);
        mainPlot.addDomainMarker(marker);
    }
    
    public void addPoint(double x, double y) {
        points.add(new Second(Date.from(Instant.ofEpochMilli((long) x))), y);
    }
    
    public void showPlot() {
        setVisible(true);
    }
    
    @Override
    protected void processWindowEvent(final WindowEvent e) {
        super.processWindowEvent(e);
        if (e.getID() == WindowEvent.WINDOW_CLOSING) {
            tseries = null;
            this.dispose();
        }
    }

    /**
     * @return the datasetInitializer
     */
    public StrategyDatasetInitializer getDatasetInitializer() {
        return datasetInitializer;
    }

    /**
     * @param datasetInitializer the datasetInitializer to set
     */
    public void setDatasetInitializer(StrategyDatasetInitializer datasetInitializer) {
        this.datasetInitializer = datasetInitializer;
    }
}
