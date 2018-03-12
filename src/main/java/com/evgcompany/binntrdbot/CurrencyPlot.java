/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.WindowEvent;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.CandlestickRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.DateRange;
import org.jfree.data.time.MovingAverage;
import org.jfree.data.xy.AbstractXYDataset;
import org.jfree.data.xy.DefaultOHLCDataset;
import org.jfree.data.xy.OHLCDataItem;
import org.jfree.data.xy.XYDataset;
import org.ta4j.core.Bar;
import org.ta4j.core.TimeSeries;

/**
 *
 * @author EVG_Adminer
 */
public class CurrencyPlot extends JFrame implements ChangeListener {
    private static final int SLIDER_INITIAL_VALUE = 100;
    private final JSlider slider;
    private final DateAxis domainAxis;
    private final XYPlot mainPlot;
    private int lastValue = SLIDER_INITIAL_VALUE;
    
    private double maxVolume = 0;

    // (milliseconds, seconds, minutes, hours, days)
    private final int delta = 1000 * 60 * 10;
    
    private TimeSeries tseries = null;
    private String stockSymbol;
    private long bar_seconds;
    
    XYDataset   dataset, maSataset, maSataset2, maSataset3;
    
    public CurrencyPlot(String _stockSymbol, TimeSeries _tseries, long _bar_seconds) {
        super(_stockSymbol + " candlestick");
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        tseries = _tseries;
        stockSymbol = _stockSymbol;
        bar_seconds = _bar_seconds;
        
        domainAxis       = new DateAxis("Date");
        NumberAxis  rangeAxis        = new NumberAxis("Price");
        NumberAxis  rangeAxis2       = new NumberAxis("Volume");
        CandlestickRenderer renderer = new CandlestickRenderer();
        dataset          = getDataSet(stockSymbol);
        
        mainPlot = new XYPlot(dataset, domainAxis, rangeAxis, renderer);
        
        renderer.setAutoWidthMethod(CandlestickRenderer.WIDTHMETHOD_SMALLEST);
        renderer.setAutoWidthGap(1);
        renderer.setAutoWidthFactor(0.75);
        
        rangeAxis2.setUpperMargin(maxVolume);
        
        XYLineAndShapeRenderer maRenderer = new XYLineAndShapeRenderer(true, false);
                               maSataset  = MovingAverage.createMovingAverage(dataset, "MA1", 7 * bar_seconds * 1000, 0);
        mainPlot.setRenderer(1, maRenderer);
        mainPlot.setDataset (1, maSataset);
        XYLineAndShapeRenderer maRenderer2 = new XYLineAndShapeRenderer(true, false);
                               maSataset2  = MovingAverage.createMovingAverage(dataset, "MA2", 25 * bar_seconds * 1000, 0);
        mainPlot.setRenderer(2, maRenderer2);
        mainPlot.setDataset (2, maSataset2);
        XYLineAndShapeRenderer maRenderer3 = new XYLineAndShapeRenderer(true, false);
                               maSataset3  = MovingAverage.createMovingAverage(dataset, "MA3", 99 * bar_seconds * 1000, 0);
        mainPlot.setRenderer(3, maRenderer3);
        mainPlot.setDataset (3, maSataset3);
        
        maRenderer.setSeriesPaint(0, Color.YELLOW);
        maRenderer.setSeriesStroke(0, new BasicStroke(2));
        maRenderer2.setSeriesPaint(0, Color.BLUE);
        maRenderer2.setSeriesStroke(0, new BasicStroke(2));
        maRenderer3.setSeriesPaint(0, Color.RED);
        maRenderer3.setSeriesStroke(0, new BasicStroke(2));
        
        mainPlot.setRangeAxis( 1, rangeAxis2 );
        
        //Do some setting up, see the API Doc
        renderer.setDrawVolume(true);
        
        rangeAxis.setAutoRange(true);
        rangeAxis.setUpperMargin(0.0D);
        rangeAxis.setLowerMargin(0.0D);
        rangeAxis.setAutoRangeIncludesZero(false);
        rangeAxis.setNumberFormatOverride(new DecimalFormat("0.########"));

        //Now create the chart and chart panel
        JFreeChart chart = new JFreeChart(stockSymbol, null, mainPlot, false);
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(600, 300));
        
        chart.setAntiAlias(false);

        chartPanel.setDomainZoomable(true);
        chartPanel.setRangeZoomable(true);
        Border border = BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(4, 4, 4, 4),
            BorderFactory.createEtchedBorder()
        );
        chartPanel.setBorder(border);
        
        add(chartPanel);
        
        JPanel dashboard = new JPanel(new BorderLayout());
        dashboard.setBorder(BorderFactory.createEmptyBorder(0, 4, 4, 4));   

        slider = new JSlider(0, 200, SLIDER_INITIAL_VALUE);
        slider.addChangeListener(this);
        dashboard.add(slider);
        add(dashboard, BorderLayout.SOUTH);
        
        pack();
    }
    
    public void updateSeries() {
        if (tseries != null) {
            dataset = getDataSet(stockSymbol);
            mainPlot.setDataset(dataset);
            maSataset  = MovingAverage.createMovingAverage(dataset, "MA1", 7 * bar_seconds * 1000, 0);
            mainPlot.setDataset (1, maSataset);
            maSataset2  = MovingAverage.createMovingAverage(dataset, "MA2", 25 * bar_seconds * 1000, 0);
            mainPlot.setDataset (2, maSataset2);
            maSataset3  = MovingAverage.createMovingAverage(dataset, "MA3", 99 * bar_seconds * 1000, 0);
            mainPlot.setDataset (3, maSataset3);
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
        mainPlot.addDomainMarker(marker);
    }
    
    @Override
        public void stateChanged(ChangeEvent event) {
            int value = this.slider.getValue();
            long minimum = domainAxis.getMinimumDate().getTime();
            long maximum = domainAxis.getMaximumDate().getTime();
            if (value<lastValue) { // left
                minimum = minimum - delta;
                maximum = maximum - delta;
            } else { // right
                minimum = minimum + delta;
                maximum = maximum + delta;
            }
            DateRange range = new DateRange(minimum,maximum);
            domainAxis.setRange(range);
            lastValue = value;
        }
    
    private AbstractXYDataset getDataSet(String stockSymbol) {
        return new DefaultOHLCDataset(stockSymbol, getData());
    }
    //This method uses yahoo finance to get the OHLC data
    protected OHLCDataItem[] getData() {
        List<OHLCDataItem> dataItems = new ArrayList<>();
        try {
            List<Bar> clist = tseries.getBarData();
            
            for(int i=0; i<clist.size(); i++) {
                Bar bar = clist.get(i);
                Date date       = Date.from(bar.getBeginTime().toInstant());
                double open     = bar.getOpenPrice().doubleValue();
                double high     = bar.getMaxPrice().doubleValue();
                double low      = bar.getMinPrice().doubleValue();
                double close    = bar.getClosePrice().doubleValue();
                double volume   = bar.getVolume().doubleValue();
                if (volume > maxVolume) {
                    maxVolume = volume;
                }
                OHLCDataItem item = new OHLCDataItem(date, open, high, low, close, volume);
                dataItems.add(item);
            }
        }
        catch (Exception e) {
        }
        //Data from Yahoo is from newest to oldest. Reverse so it is oldest to newest

        //Convert the list into an array
        OHLCDataItem[] data = dataItems.toArray(new OHLCDataItem[dataItems.size()]);

        return data;
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
}
