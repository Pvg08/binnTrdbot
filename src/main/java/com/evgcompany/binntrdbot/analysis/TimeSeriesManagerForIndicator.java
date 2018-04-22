/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Decimal;
import org.ta4j.core.Order;
import org.ta4j.core.Strategy;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.AbstractIndicator;

/**
 *
 * @author EVG_Adminer
 */
public class TimeSeriesManagerForIndicator {

    /**
     * The logger
     */
    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * The managed time series
     */
    private TimeSeries timeSeries;
    private AbstractIndicator<Decimal> indicator;

    /**
     * Constructor.
     */
    public TimeSeriesManagerForIndicator() {
    }

    /**
     * Constructor.
     *
     * @param timeSeries the time series to be managed
     * @param indicator
     */
    public TimeSeriesManagerForIndicator(TimeSeries timeSeries, AbstractIndicator<Decimal> indicator) {
        this.timeSeries = timeSeries;
        this.indicator = indicator;
    }

    /**
     * @param timeSeries the time series to be managed
     */
    public void setTimeSeries(TimeSeries timeSeries) {
        this.timeSeries = timeSeries;
    }

    /**
     * @return the managed time series
     */
    public TimeSeries getTimeSeries() {
        return timeSeries;
    }

    /**
     * Runs the provided strategy over the managed series.
     * <p>
     * Opens the trades with {@link OrderType} BUY @return the trading record
     * coming from the run
     * @param strategy
     * @return 
     */
    public TradingRecord run(Strategy strategy) {
        return run(strategy, Order.OrderType.BUY);
    }

    /**
     * Runs the provided strategy over the managed series (from startIndex to
     * finishIndex).
     * <p>
     * Opens the trades with {@link OrderType} BUY orders.
     *
     * @param strategy the trading strategy
     * @param startIndex the start index for the run (included)
     * @param finishIndex the finish index for the run (included)
     * @return the trading record coming from the run
     */
    public TradingRecord run(Strategy strategy, int startIndex, int finishIndex) {
        return run(strategy, Order.OrderType.BUY, Decimal.NaN, startIndex, finishIndex);
    }

    /**
     * Runs the provided strategy over the managed series.
     * <p>
     * Opens the trades with {@link OrderType} BUY orders.
     *
     * @param strategy the trading strategy
     * @param orderType the {@link OrderType} used to open the trades
     * @return the trading record coming from the run
     */
    public TradingRecord run(Strategy strategy, Order.OrderType orderType) {
        return run(strategy, orderType, Decimal.NaN);
    }

    /**
     * Runs the provided strategy over the managed series (from startIndex to
     * finishIndex).
     * <p>
     * Opens the trades with {@link OrderType} BUYorders.
     *
     * @param strategy the trading strategy
     * @param orderType the {@link OrderType} used to open the trades
     * @param startIndex the start index for the run (included)
     * @param finishIndex the finish index for the run (included)
     * @return the trading record coming from the run
     */
    public TradingRecord run(Strategy strategy, Order.OrderType orderType, int startIndex, int finishIndex) {
        return run(strategy, orderType, Decimal.NaN, startIndex, finishIndex);
    }

    /**
     * Runs the provided strategy over the managed series.
     * <p>
     * @param strategy the trading strategy
     * @param orderType the {@link OrderType} used to open the trades
     * @param amount the amount used to open/close the trades
     * @return the trading record coming from the run
     */
    public TradingRecord run(Strategy strategy, Order.OrderType orderType, Decimal amount) {
        return run(strategy, orderType, amount, timeSeries.getBeginIndex(), timeSeries.getEndIndex());
    }

    /**
     * Runs the provided strategy over the managed series (from startIndex to
     * finishIndex).
     * <p>
     * @param strategy the trading strategy
     * @param orderType the {@link OrderType} used to open the trades
     * @param amount the amount used to open/close the trades
     * @param startIndex the start index for the run (included)
     * @param finishIndex the finish index for the run (included)
     * @return the trading record coming from the run
     */
    public TradingRecord run(Strategy strategy, Order.OrderType orderType, Decimal amount, int startIndex, int finishIndex) {

        int runBeginIndex = Math.max(startIndex, timeSeries.getBeginIndex());
        int runEndIndex = Math.min(finishIndex, timeSeries.getEndIndex());

        log.trace("Running strategy (indexes: {} -> {}): {} (starting with {})", runBeginIndex, runEndIndex, strategy, orderType);
        TradingRecord tradingRecord = new BaseTradingRecord(orderType);
        for (int i = runBeginIndex; i <= runEndIndex; i++) {
            // For each bar between both indexes...
            if (strategy.shouldOperate(i, tradingRecord)) {
                tradingRecord.operate(i, indicator.getValue(i), amount);
            }
        }

        if (!tradingRecord.isClosed()) {
            // If the last trade is still opened, we search out of the run end index.
            // May works if the end index for this run was inferior to the actual number of bars
            int seriesMaxSize = Math.max(timeSeries.getEndIndex() + 1, timeSeries.getBarData().size());
            for (int i = runEndIndex + 1; i < seriesMaxSize; i++) {
                // For each bar after the end index of this run...
                // --> Trying to close the last trade
                if (strategy.shouldOperate(i, tradingRecord)) {
                    tradingRecord.operate(i, indicator.getValue(i), amount);
                    break;
                }
            }
        }
        return tradingRecord;
    }

    /**
     * @return the indicator
     */
    public AbstractIndicator<Decimal> getIndicator() {
        return indicator;
    }

    /**
     * @param indicator the indicator to set
     */
    public void setIndicator(AbstractIndicator<Decimal> indicator) {
        this.indicator = indicator;
    }
}
