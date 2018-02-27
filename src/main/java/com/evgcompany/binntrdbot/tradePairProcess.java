/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.account.Order;
import com.binance.api.client.domain.account.Trade;
import com.binance.api.client.domain.account.request.CancelOrderRequest;
import com.binance.api.client.domain.account.request.OrderRequest;
import com.binance.api.client.domain.account.request.OrderStatusRequest;
import com.binance.api.client.domain.event.CandlestickEvent;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.SymbolFilter;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.exception.BinanceApiException;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.Decimal;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.TimeSeriesManager;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.criteria.AverageProfitCriterion;
import org.ta4j.core.analysis.criteria.AverageProfitableTradesCriterion;
import org.ta4j.core.analysis.criteria.BuyAndHoldCriterion;
import org.ta4j.core.analysis.criteria.LinearTransactionCostCriterion;
import org.ta4j.core.analysis.criteria.MaximumDrawdownCriterion;
import org.ta4j.core.analysis.criteria.NumberOfTradesCriterion;
import org.ta4j.core.analysis.criteria.RewardRiskRatioCriterion;
import org.ta4j.core.analysis.criteria.TotalProfitCriterion;
import org.ta4j.core.analysis.criteria.VersusBuyAndHoldCriterion;
import org.ta4j.core.indicators.AroonUpIndicator;
import org.ta4j.core.indicators.CCIIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.FisherIndicator;
import org.ta4j.core.indicators.HMAIndicator;
import org.ta4j.core.indicators.KAMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.RandomWalkIndexHighIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.StochasticOscillatorKIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.indicators.helpers.MaxPriceIndicator;
import org.ta4j.core.indicators.helpers.MinPriceIndicator;
import org.ta4j.core.indicators.helpers.MultiplierIndicator;
import org.ta4j.core.indicators.helpers.OpenPriceIndicator;
import org.ta4j.core.indicators.helpers.TypicalPriceIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuChikouSpanIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuKijunSenIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanAIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanBIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuTenkanSenIndicator;
import org.ta4j.core.trading.rules.BooleanRule;
import org.ta4j.core.trading.rules.CrossedDownIndicatorRule;
import org.ta4j.core.trading.rules.CrossedUpIndicatorRule;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;

class StrategyMarker {
    String label = "";
    double timeStamp = 0;
    int typeIndex = 0;
}

/**
 *
 * @author EVG_adm_T
 */
public class tradePairProcess extends Thread {
    //BinanceApiWebSocketClient client_s = null;
    BinanceApiRestClient client;
    private String symbol;
    private String baseAssetSymbol;
    private String quoteAssetSymbol;
    private tradeProfitsController profitsChecker = null;
    private mainApplication app;
    private boolean need_stop = false;
    private boolean paused = false;
    
    private boolean isTryingToSellUp = false;
    private boolean isTryingToBuyDip = false;
    private boolean sellUpAll = false;
    private boolean lowHold = true;
    private boolean checkOtherStrategies = true;
    
    private List<Long> orderToCancelOnSellUp = new ArrayList<>();
    private long last_time = 0;
    
    private float baseStartBalance;
    private float quoteStartBalance;
    
    private long limitOrderId = 0;
    
    private float tradingBalancePercent = 50f;
    private float tradeMinProfitPercent = 0.03f;
    
    private boolean is_hodling = false;
    private float sold_price = 0f;
    private float sold_amount = 0f;
    
    private long delayTime = 10;
    
    private List<StrategyMarker> markers = new ArrayList<>();
    
    private CurrencyPlot plot = null;
    private TimeSeries series = null;
    HashMap<String, Strategy> strategies = new HashMap<String, Strategy>();
    
    private String mainStrategy = "Auto";
    private CandlestickInterval barInterval;
    private int barSeconds;
    private boolean need_bar_reset = false;
    private boolean buyOnStart = false;
    
    private boolean base_strategy_sell_ignored = false;
    private boolean do_remove_flag = false;
    
    private boolean filterPrice = false;
    private float filterPriceTickSize = 0;
    private float filterMinPrice = 0;
    private float filterMaxPrice = 0;
    private boolean filterQty = false;
    private float filterQtyStep = 0;
    private float filterMinQty = 0;
    private float filterMaxQty = 0;
    private boolean filterNotional = false;
    private float filterMinNotional = 0;
    
    private static DecimalFormat df5 = new DecimalFormat("0.#####");
    private static DecimalFormat df8 = new DecimalFormat("0.########");
    private int startDelayTime = 0;
    
    public tradePairProcess(mainApplication application, BinanceApiRestClient rclient, tradeProfitsController rprofitsChecker, String pair) {
        app = application;
        symbol = pair;
        client = rclient;
        profitsChecker = rprofitsChecker;
        setBarInterval("1m");
        /*client_s = BinanceApiClientFactory.newInstance().newWebSocketClient();
        client_s.onCandlestickEvent(pair, CandlestickInterval.ONE_MINUTE, response -> {
            tradeResponse(response);
        });*/
    }
    
    private void doEnter(float curPrice) {
        float summ_to_buy = quoteStartBalance * tradingBalancePercent / 100.0f;
        sold_price = normalizePrice(curPrice);
        sold_amount = normalizeQuantity(summ_to_buy / curPrice, true);
        sold_amount = normalizeNotionalQuantity(sold_amount, curPrice);
        summ_to_buy = sold_price * sold_amount;
        base_strategy_sell_ignored = false;
        if (profitsChecker.canBuy(symbol, sold_amount, curPrice)) {
            app.log("BYING " + df8.format(sold_amount) + " " + baseAssetSymbol + "  for  " + df8.format(summ_to_buy) + " " + quoteAssetSymbol + " (price=" + df8.format(curPrice) + ")\n", true, true);
            long result = profitsChecker.Buy(symbol, sold_amount, curPrice, false);
            if (result >= 0) {
                limitOrderId = result;
                app.log("Successful!\n", true, true);
                is_hodling = true;
            } else {
                app.log("Error!\n", true, true);
            }
        } else {
            app.log("Can't buy " + df8.format(sold_amount) + " " + baseAssetSymbol + "  for  " + df8.format(summ_to_buy) + " " + quoteAssetSymbol + " (price=" + df8.format(curPrice) + ")\n");
        }
    }
    private void doExit(float curPrice, boolean skip_check) {
        float new_sold_price = sold_amount * curPrice;
        float incomeWithoutComission = sold_amount * (curPrice * (1 - 0.01f * profitsChecker.getTradeComissionPercent()) - sold_price);
        float incomeWithoutComissionPercent = 100 *  incomeWithoutComission / (sold_price * sold_amount);
        if (skip_check || !lowHold || incomeWithoutComissionPercent > tradeMinProfitPercent) {
            if (profitsChecker.canSell(symbol, sold_amount)) {
                base_strategy_sell_ignored = false;
                app.log("SELLING " + df8.format(sold_amount) + " " + baseAssetSymbol + "  for  " + df8.format(new_sold_price) + " " + quoteAssetSymbol + " (price=" + df8.format(curPrice) + ")", true, true);
                long result = profitsChecker.Sell(symbol, sold_amount, curPrice, orderToCancelOnSellUp);
                if (result >= 0) {
                    limitOrderId = result;
                    app.log("Successful!", true, true);
                    app.log("RESULT: " + df8.format(incomeWithoutComission) + " " + quoteAssetSymbol + " (" + df8.format(incomeWithoutComissionPercent) + "%)\n", true);
                    is_hodling = false;
                    orderToCancelOnSellUp.clear();
                    isTryingToSellUp = false;
                } else {
                    app.log("Error!\n", true, true);
                }
            } else {
                app.log("Can't sell " + sold_amount + " " + symbol + "\n", false, true);
            }
        } else {
            app.log(symbol + " - need to exit but profit ("+incomeWithoutComissionPercent+"%) is too low. Waiting...\n", false, true);
            base_strategy_sell_ignored = true;
        }
    }
    
    private void checkOrder() {
        Order order = client.getOrderStatus(new OrderStatusRequest(symbol, limitOrderId));
        if (order != null && (
                order.getStatus() == OrderStatus.FILLED || 
                order.getStatus() == OrderStatus.CANCELED ||
                order.getStatus() == OrderStatus.EXPIRED ||
                order.getStatus() == OrderStatus.REJECTED
            )
        ) {
            limitOrderId = 0;
            if (order.getStatus() != OrderStatus.FILLED) {
                if(order.getSide() == OrderSide.BUY) {
                    isTryingToSellUp = false;
                    is_hodling = false;
                    orderToCancelOnSellUp.clear();
                    base_strategy_sell_ignored = false;
                } else {
                    is_hodling = true;
                }
            }
            profitsChecker.finishOrder(symbol, order.getStatus() == OrderStatus.FILLED, sold_price);
            profitsChecker.updateAllBalances(true);
            app.log("Limit order for "+order.getSide().name().toLowerCase()+" "+symbol+" is finished! Status="+order.getStatus().name()+"; Price = "+order.getPrice(), true, true);
        }
        int endIndex = series.getEndIndex();
        if (endIndex >= 0 && endIndex < series.getBarCount()) {
            float curPrice = series.getBar(endIndex).getClosePrice().floatValue();
            profitsChecker.setPairPrice(symbol, curPrice);
        }
    }
    
    private void addStrategyMarker(boolean is_enter, String strategy_name) {
        StrategyMarker newm = new StrategyMarker();
        newm.label = (is_enter ? "Enter" : "Exit") + " (" + strategy_name + ")";
        newm.timeStamp = System.currentTimeMillis();
        if (strategy_name.equals(mainStrategy)) {
            newm.typeIndex = is_enter ? 0 : 1;
        } else {
            newm.typeIndex = is_enter ? 2 : 3;
        }
        markers.add(newm);
    }
    
    private void addStrategyPastMarker(boolean is_enter, long timestamp, String strategy_name) {
        StrategyMarker newm = new StrategyMarker();
        newm.label = (is_enter ? "Enter" : "Exit") + " (" + strategy_name + ")";
        newm.timeStamp = timestamp;
        newm.typeIndex = is_enter ? 0 : 1;
        markers.add(newm);
    }
    
    private void checkStatus() {
        
        int endIndex = series.getEndIndex();
        float curPrice = series.getBar(endIndex).getClosePrice().floatValue();
        
        if (is_hodling) {
            if (!isTryingToBuyDip) {
                if (strategies.get(mainStrategy).shouldExit(endIndex)) {
                    addStrategyMarker(false, mainStrategy);
                    doExit(curPrice, false);
                } else if (strategies.get(mainStrategy).shouldEnter(endIndex)) {
                    addStrategyMarker(true, mainStrategy);
                    app.log(symbol + " should enter here but already hodling...", false, true);
                }
            }
        } else {
            if (strategies.get(mainStrategy).shouldEnter(endIndex)) {
                addStrategyMarker(true, mainStrategy);
                doEnter(curPrice);
            } else if (strategies.get(mainStrategy).shouldExit(endIndex)) {
                addStrategyMarker(false, mainStrategy);
                app.log(symbol + " should exit here but everything is sold...", false, true);
            }
        }

        if (!isTryingToBuyDip && !buyOnStart && checkOtherStrategies) {
            for (Map.Entry<String, Strategy> entry : strategies.entrySet()) {
                if (entry.getKey() != mainStrategy) {
                    if (is_hodling) {
                        if (entry.getValue().shouldExit(endIndex)) {
                            addStrategyMarker(false, entry.getKey());
                            app.log(symbol + " should exit here ("+entry.getKey()+")...", false, true);
                            if (base_strategy_sell_ignored) {
                                doExit(curPrice, false);
                            }
                        } else if (entry.getValue().shouldEnter(endIndex)) {
                            addStrategyMarker(true, entry.getKey());
                            app.log(symbol + " should enter here ("+entry.getKey()+") but already hodling...", false, true);
                        }
                    } else {
                        if (entry.getValue().shouldEnter(endIndex)) {
                            addStrategyMarker(true, entry.getKey());
                            app.log(symbol + " should enter here ("+entry.getKey()+")...", false, true);
                        } else if (entry.getValue().shouldExit(endIndex)) {
                            addStrategyMarker(false, entry.getKey());
                            app.log(symbol + " should exit here ("+entry.getKey()+") but everything is sold...", false, true);
                        }
                    }
                }
            }
        }
        
        profitsChecker.setPairPrice(symbol, curPrice);
    }
    
    public void doStop() {
        /*try {
            ((BinanceApiWebSocketClientImpl) client_s).close();
        } catch(Exception e) {}
        client_s = null;*/
        need_stop = true;
        paused = false;
    }
    
    void doSetPaused(boolean _paused) {
        paused = _paused;
    }
    
    private void tradeResponse(CandlestickEvent response) {
        app.log(symbol + " = " + response.getClose(), false, true);
    }
    
    private float getQuoteBalance() {
        AssetBalance qbalance = profitsChecker.getAccount().getAssetBalance(quoteAssetSymbol);
        return Float.parseFloat(qbalance.getFree());
    }
    private float getBaseBalance() {
        AssetBalance qbalance = profitsChecker.getAccount().getAssetBalance(baseAssetSymbol);
        return Float.parseFloat(qbalance.getFree());
    }
    
    private void doWait(long ms) {
        try { Thread.sleep(ms);} catch(InterruptedException e) {}
    }
    
    private void addBars(List<Candlestick> nbars) {
        if (nbars.size() > 0) {
            ZonedDateTime lastEndTime = null;
            if (series.getBarCount() > 0) {
                long last_end_time = series.getLastBar().getEndTime().toInstant().toEpochMilli();
                lastEndTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(last_end_time), ZoneId.systemDefault());
                
                //System.out.println(last_end_time);
                //System.out.println(lastEndTime);
            }
            
            //System.out.println(lastEndTime);
            
            for(int i=0; i<nbars.size(); i++) {
                
                //System.out.println(i);
                //System.out.println(nbars.get(i));
                
                Candlestick stick = nbars.get(i);
                Duration barDuration = Duration.ofSeconds(barSeconds);
                ZonedDateTime endTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(stick.getCloseTime()), ZoneId.systemDefault());
                Bar newbar = new BaseBar(barDuration, endTime, Decimal.valueOf(stick.getOpen()), Decimal.valueOf(stick.getHigh()), Decimal.valueOf(stick.getLow()), Decimal.valueOf(stick.getClose()), Decimal.valueOf(stick.getVolume()), Decimal.valueOf(stick.getQuoteAssetVolume()));
                boolean updated = false;
                if (lastEndTime != null) {
                    //System.out.println(lastEndTime + "   ***   " + endTime);
                    if (lastEndTime.equals(endTime)) {
                        List<Bar> clist = series.getBarData();
                        clist.set(clist.size()-1, newbar);
                        updated = true;
                        //System.out.println("last bar Updated!");
                    } else if (lastEndTime.isAfter(endTime)) {
                        updated = true;
                        //System.out.println("bad bar!");
                    }
                }
                if (!updated) {
                    series.addBar(newbar);
                    //System.out.println("new bar added!");
                }
                //System.out.println(newbar);
            }
        }
    }
    
    /**
     * @param series a time series
     * @return a dummy strategy
     */
    private Strategy buildNoStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        return new BaseStrategy(
                new BooleanRule(false),
                new BooleanRule(false)
        );
    }
    
    /**
     * @param series a time series
     * @return a dummy strategy
     */
    private Strategy buildSimpleStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator sma = new SMAIndicator(closePrice, 12);

        // Signals
        // Buy when SMA goes over close price
        // Sell when close price goes over SMA
        return new BaseStrategy(
                new OverIndicatorRule(sma, closePrice),
                new UnderIndicatorRule(sma, closePrice)
        );
    }

    
    /**
     * @param series a time series
     * @return a AdvancedEMA strategy
     */
    private Strategy buildAdvancedEMAStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        EMAIndicator ema_y = new EMAIndicator(closePrice, 55);
        EMAIndicator ema_1 = new EMAIndicator(closePrice, 21);
        EMAIndicator ema_2 = new EMAIndicator(closePrice, 13);
        EMAIndicator ema_3 = new EMAIndicator(closePrice, 8);
        
        // Entry rule
        Rule entryRule = new UnderIndicatorRule(ema_y, ema_1)
                .and(new UnderIndicatorRule(ema_y, ema_2))
                .and(new UnderIndicatorRule(ema_y, ema_3));
        
        // Exit rule
        Rule exitRule = new OverIndicatorRule(ema_y, ema_1)
                .and(new OverIndicatorRule(ema_y, ema_2))
                .and(new OverIndicatorRule(ema_y, ema_3));
        
        return new BaseStrategy(entryRule, exitRule);
    }
    
    
    /**
     * @param series a time series
     * @return a Ichimoku strategy
     */
    private Strategy buildIchimokuStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        IchimokuTenkanSenIndicator tenkanSen = new IchimokuTenkanSenIndicator(series, 3);
        IchimokuKijunSenIndicator kijunSen = new IchimokuKijunSenIndicator(series, 5);
        IchimokuSenkouSpanAIndicator senkouSpanA = new IchimokuSenkouSpanAIndicator(series, tenkanSen, kijunSen);
        IchimokuSenkouSpanBIndicator senkouSpanB = new IchimokuSenkouSpanBIndicator(series, 9);
        IchimokuChikouSpanIndicator chikouSpan = new IchimokuChikouSpanIndicator(series, 5);
        
        // Entry rule
        Rule entryRule = new CrossedDownIndicatorRule(kijunSen, tenkanSen);
        
        // Exit rule
        Rule exitRule = new CrossedUpIndicatorRule(kijunSen, tenkanSen);
        
        return new BaseStrategy(entryRule, exitRule);
    }
    
    /**
     * @param series a time series
     * @return a Ichimoku strategy
     */
    private Strategy buildIchimoku2Strategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        IchimokuTenkanSenIndicator tenkanSen = new IchimokuTenkanSenIndicator(series, 3);
        IchimokuKijunSenIndicator kijunSen = new IchimokuKijunSenIndicator(series, 5);
        IchimokuSenkouSpanAIndicator senkouSpanA = new IchimokuSenkouSpanAIndicator(series, tenkanSen, kijunSen);
        IchimokuSenkouSpanBIndicator senkouSpanB = new IchimokuSenkouSpanBIndicator(series, 9);
        IchimokuChikouSpanIndicator chikouSpan = new IchimokuChikouSpanIndicator(series, 5);
        
        // Entry rule
        Rule entryRule = new CrossedDownIndicatorRule(closePrice, kijunSen);
        
        // Exit rule
        Rule exitRule = new CrossedUpIndicatorRule(closePrice, kijunSen);
        
        return new BaseStrategy(entryRule, exitRule);
    }
    
    /**
     * @param series a time series
     * @return a Ichimoku strategy
     */
    private Strategy buildIchimoku3Strategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        IchimokuTenkanSenIndicator tenkanSen = new IchimokuTenkanSenIndicator(series, 3);
        IchimokuKijunSenIndicator kijunSen = new IchimokuKijunSenIndicator(series, 5);
        IchimokuSenkouSpanAIndicator senkouSpanA = new IchimokuSenkouSpanAIndicator(series, tenkanSen, kijunSen);
        IchimokuSenkouSpanBIndicator senkouSpanB = new IchimokuSenkouSpanBIndicator(series, 9);
        IchimokuChikouSpanIndicator chikouSpan = new IchimokuChikouSpanIndicator(series, 5);
        
        // Entry rule
        Rule entryRule = new CrossedDownIndicatorRule(closePrice, senkouSpanB);
        
        // Exit rule
        Rule exitRule = new CrossedUpIndicatorRule(closePrice, senkouSpanB);
        
        return new BaseStrategy(entryRule, exitRule);
    }
    
    /**
     * @param series a time series
     * @return a dummy strategy
     */
    private Strategy buildMyMainStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        // http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ichimoku_cloud
        /*IchimokuTenkanSenIndicator tenkanSen = new IchimokuTenkanSenIndicator(series, 3);
        IchimokuKijunSenIndicator kijunSen = new IchimokuKijunSenIndicator(series, 5);
        IchimokuSenkouSpanAIndicator senkouSpanA = new IchimokuSenkouSpanAIndicator(series, tenkanSen, kijunSen);
        IchimokuSenkouSpanBIndicator senkouSpanB = new IchimokuSenkouSpanBIndicator(series, 9);
        IchimokuChikouSpanIndicator chikouSpan = new IchimokuChikouSpanIndicator(series, 5);*/
        
        // http://www.tradingsystemlab.com/files/The%20Fisher%20Transform.pdf
        // FisherIndicator fisher = new FisherIndicator(series);

        // https://alanhull.com/hull-moving-average
        // HMAIndicator hma = new HMAIndicator(new ClosePriceIndicator(series), 9);
        
        // http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:kaufman_s_adaptive_moving_average
        // KAMAIndicator kama = new KAMAIndicator(new ClosePriceIndicator(series), 10, 2, 30);
        
        // http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:aroon
        // AroonUpIndicator arronUp = new AroonUpIndicator(series, 5);
        
        
        
        
        
        // https://blokt.com/technical-analysis/cryptocurrency-indicators
        // https://hackernoon.com/my-top-3-favourite-indicators-for-technical-analysis-of-cryptocurrencies-b552f584776d
        
        
        
        
        
        
        //OpenPriceIndicator openPriceIndicator = new OpenPriceIndicator(series);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        
        // The bias is bullish when the shorter-moving average moves above the longer moving average.
        // The bias is bearish when the shorter-moving average moves below the longer moving average.
        EMAIndicator shortEma = new EMAIndicator(closePrice, 7);
        EMAIndicator longEma = new EMAIndicator(closePrice, 25);

        MACDIndicator macd = new MACDIndicator(closePrice, 9, 26);
        EMAIndicator emaMacd = new EMAIndicator(macd, 18);
        
        // Entry rule
        Rule entryRule = new OverIndicatorRule(shortEma, longEma)
                .and(new OverIndicatorRule(macd, emaMacd));
        
        // Exit rule
        Rule exitRule = new UnderIndicatorRule(shortEma, longEma)
                .and(new UnderIndicatorRule(macd, emaMacd));
        
        return new BaseStrategy(entryRule, exitRule);
    }
    
    /**
     * @param series a time series
     * @return a moving momentum strategy
     */
    public Strategy buildMovingMomentumStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        
        // The bias is bullish when the shorter-moving average moves above the longer moving average.
        // The bias is bearish when the shorter-moving average moves below the longer moving average.
        EMAIndicator shortEma = new EMAIndicator(closePrice, 9);
        EMAIndicator longEma = new EMAIndicator(closePrice, 26);

        StochasticOscillatorKIndicator stochasticOscillK = new StochasticOscillatorKIndicator(series, 14);

        MACDIndicator macd = new MACDIndicator(closePrice, 9, 26);
        EMAIndicator emaMacd = new EMAIndicator(macd, 18);
        
        // Entry rule
        Rule entryRule = new OverIndicatorRule(shortEma, longEma) // Trend
                .and(new CrossedDownIndicatorRule(stochasticOscillK, Decimal.valueOf(20))) // Signal 1
                .and(new OverIndicatorRule(macd, emaMacd)); // Signal 2
        
        // Exit rule
        Rule exitRule = new UnderIndicatorRule(shortEma, longEma) // Trend
                .and(new CrossedUpIndicatorRule(stochasticOscillK, Decimal.valueOf(80))) // Signal 1
                .and(new UnderIndicatorRule(macd, emaMacd)); // Signal 2
        
        return new BaseStrategy(entryRule, exitRule);
    }

    /**
     * @param series a time series
     * @return a CCI correction strategy
     */
    public Strategy buildCCICorrectionStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        CCIIndicator longCci = new CCIIndicator(series, 200);
        CCIIndicator shortCci = new CCIIndicator(series, 5);
        Decimal plus100 = Decimal.HUNDRED;
        Decimal minus100 = Decimal.valueOf(-100);
        
        Rule entryRule = new OverIndicatorRule(longCci, plus100) // Bull trend
                .and(new UnderIndicatorRule(shortCci, minus100)); // Signal
        
        Rule exitRule = new UnderIndicatorRule(longCci, minus100) // Bear trend
                .and(new OverIndicatorRule(shortCci, plus100)); // Signal
        
        Strategy strategy = new BaseStrategy(entryRule, exitRule);
        strategy.setUnstablePeriod(5);
        return strategy;
    }
    
    /**
     * @param series a time series
     * @return a global extrema strategy
     */
    public Strategy buildGlobalExtremaStrategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        int bars_per_day = Math.round(60f * (60f / barSeconds) * 24f);
        
        ClosePriceIndicator closePrices = new ClosePriceIndicator(series);

        // Getting the max price over the past day
        MaxPriceIndicator maxPrices = new MaxPriceIndicator(series);
        HighestValueIndicator dayMaxPrice = new HighestValueIndicator(maxPrices, bars_per_day);
        // Getting the min price over the past day
        MinPriceIndicator minPrices = new MinPriceIndicator(series);
        LowestValueIndicator dayMinPrice = new LowestValueIndicator(minPrices, bars_per_day);

        // Going long if the close price goes below the min price
        MultiplierIndicator downDay = new MultiplierIndicator(dayMinPrice, Decimal.valueOf("1.005"));
        Rule buyingRule = new UnderIndicatorRule(closePrices, downDay);

        // Going short if the close price goes above the max price
        MultiplierIndicator upDay = new MultiplierIndicator(dayMaxPrice, Decimal.valueOf("0.995"));
        Rule sellingRule = new OverIndicatorRule(closePrices, upDay);

        return new BaseStrategy(buyingRule, sellingRule);
    }
    
    /**
     * @param series a time series
     * @return a 2-period RSI strategy
     */
    public static Strategy buildRSI2Strategy(TimeSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator shortSma = new SMAIndicator(closePrice, 5);
        SMAIndicator longSma = new SMAIndicator(closePrice, 200);

        // We use a 2-period RSI indicator to identify buying
        // or selling opportunities within the bigger trend.
        RSIIndicator rsi = new RSIIndicator(closePrice, 2);
        
        // Entry rule
        // The long-term trend is up when a security is above its 200-period SMA.
        Rule entryRule = new OverIndicatorRule(shortSma, longSma) // Trend
                .and(new CrossedDownIndicatorRule(rsi, Decimal.valueOf(5))) // Signal 1
                .and(new OverIndicatorRule(shortSma, closePrice)); // Signal 2
        
        // Exit rule
        // The long-term trend is down when a security is below its 200-period SMA.
        Rule exitRule = new UnderIndicatorRule(shortSma, longSma) // Trend
                .and(new CrossedUpIndicatorRule(rsi, Decimal.valueOf(95))) // Signal 1
                .and(new UnderIndicatorRule(shortSma, closePrice)); // Signal 2
        
        // TODO: Finalize the strategy
        
        return new BaseStrategy(entryRule, exitRule);
    }
    
    public float normalizeQuantity(float qty, boolean qty_down_only) {
        if (filterQty) {
            float pqty = qty;
            if (filterQtyStep > 0) {
                qty = Math.round(qty / filterQtyStep) * filterQtyStep;
            }
            if (qty < filterMinQty) {
                qty = filterMinQty;
            } else if (qty > filterMaxQty) {
                qty = filterMaxQty;
            }
            if (qty_down_only && qty > pqty) {
                qty -= filterQtyStep;
                if (qty < 0) {
                    qty = 0;
                }
            }
        }
        return qty;
    }
    public float normalizePrice(float price) {
        if (filterPrice) {
            if (filterPriceTickSize > 0) {
                price = Math.round(price / filterPriceTickSize) * filterPriceTickSize;
            }
            if (price < filterMinPrice) {
                price = filterMinPrice;
            } else if (price > filterMaxPrice) {
                price = filterMaxPrice;
            }
        }
        return price;
    }
    public float normalizeNotionalQuantity(float quantity, float price) {
        if (filterNotional) {
            if (quantity*price < filterMinNotional) {
                quantity = filterMinNotional / price;
                quantity = normalizeQuantity(quantity, false);
            }
        }
        return quantity;
    }
    
    private void resetStrategies() {
        markers.clear();
        strategies.clear();
        strategies.put("MovingMomentum", buildMovingMomentumStrategy(series));
        strategies.put("CCICorrection", buildCCICorrectionStrategy(series));
        strategies.put("RSI2", buildRSI2Strategy(series));
        strategies.put("GlobalExtrema", buildGlobalExtremaStrategy(series));
        strategies.put("Simple MA", buildSimpleStrategy(series));
        strategies.put("Advanced EMA", buildAdvancedEMAStrategy(series));
        strategies.put("Ichimoku", buildIchimokuStrategy(series));
        strategies.put("Ichimoku2", buildIchimoku2Strategy(series));
        strategies.put("Ichimoku3", buildIchimoku3Strategy(series));
        strategies.put("My WIP Strategy", buildMyMainStrategy(series));
        strategies.put("No strategy", buildNoStrategy(series));
        if (mainStrategy.equals("Auto")) {
            mainStrategy = findOptimalStrategy();
            app.log("Optimal strategy for " + symbol + " is " + mainStrategy, true, false);
        }
        if (!strategies.containsKey(mainStrategy)) {
            List<String> keysAsArray = new ArrayList<String>(strategies.keySet());
            Random r = new Random();
            mainStrategy = keysAsArray.get(r.nextInt(keysAsArray.size()));
            app.log("Strategy for " + symbol + " is not found. Using random = " + mainStrategy, true, false);
        }

        TimeSeriesManager seriesManager = new TimeSeriesManager(series);
        TradingRecord tradingRecord = seriesManager.run(strategies.get(mainStrategy));
        
        List<org.ta4j.core.Trade> trlist = tradingRecord.getTrades();
        for(int k=0; k<trlist.size(); k++) {
            int index;
            org.ta4j.core.Trade rtrade = trlist.get(k);
            if (rtrade.getEntry() != null) {
                index = rtrade.getEntry().getIndex();
                addStrategyPastMarker(true, series.getBar(index).getBeginTime().toInstant().toEpochMilli(), mainStrategy);
            }
            if (rtrade.getExit() != null) {
                index = rtrade.getExit().getIndex();
                addStrategyPastMarker(false, series.getBar(index).getEndTime().toInstant().toEpochMilli(), mainStrategy);
            }
        }

        /**
         * Analysis criteria
         */
        app.log("");
        app.log("Current strategy ("+mainStrategy+") criterias:");
        TotalProfitCriterion totalProfit = new TotalProfitCriterion();
        app.log("Total profit: " + totalProfit.calculate(series, tradingRecord));
        app.log("Average profit (per tick): " + new AverageProfitCriterion().calculate(series, tradingRecord));
        app.log("Number of trades: " + new NumberOfTradesCriterion().calculate(series, tradingRecord));
        app.log("Profitable trades ratio: " + new AverageProfitableTradesCriterion().calculate(series, tradingRecord));
        app.log("Maximum drawdown: " + new MaximumDrawdownCriterion().calculate(series, tradingRecord));
        app.log("Reward-risk ratio: " + new RewardRiskRatioCriterion().calculate(series, tradingRecord));
        app.log("Total transaction cost (from $1000): " + new LinearTransactionCostCriterion(1000, 0.01f * profitsChecker.getTradeComissionPercent()).calculate(series, tradingRecord));
        app.log("Buy-and-hold: " + new BuyAndHoldCriterion().calculate(series, tradingRecord));
        app.log("Custom strategy profit vs buy-and-hold strategy profit: " + new VersusBuyAndHoldCriterion(totalProfit).calculate(series, tradingRecord));
        app.log("");
    }
    
    private String findOptimalStrategy() {
        String result = "Unknown";
        List<Strategy> slist = new ArrayList<Strategy>(strategies.values());
        slist.add(0, this.buildNoStrategy(series));
        List<TimeSeries> subseries = splitSeries(series, Duration.ofSeconds(barSeconds * 30), Duration.ofSeconds(barSeconds * 720));
        AnalysisCriterion profitCriterion = new TotalProfitCriterion();
        Map<String, Integer> successMap = new HashMap<String, Integer>();
        
        String log = "";
        
        for (TimeSeries slice : subseries) {
            // For each sub-series...
            log += "Sub-series: " + slice.getSeriesPeriodDescription() + "\n";
            TimeSeriesManager sliceManager = new TimeSeriesManager(slice);
            for (Entry<String, Strategy> entry : strategies.entrySet()) {
                Strategy strategy = entry.getValue();
                TradingRecord tradingRecord = sliceManager.run(strategy);
                double profit = profitCriterion.calculate(slice, tradingRecord);
                log += "\tProfit for " + entry.getKey() + ": " + profit + "\n";
            }
            Strategy bestStrategy = profitCriterion.chooseBest(sliceManager, slist);
            
            String bestKey = result;
            for (Entry<String, Strategy> entry : strategies.entrySet()) {
                if (Objects.equals(bestStrategy, entry.getValue())) {
                    bestKey = entry.getKey();
                }
            }
            if (!successMap.containsKey(bestKey)) {
                successMap.put(bestKey, 0);
            }
            successMap.put(bestKey, successMap.get(bestKey)+1);
            log += "\t\t--> Best strategy: " + bestKey + "\n";
        }
        
        int max_value = 0;
        if (successMap.containsKey(result)) {
            successMap.remove(result);
        }
        for (Entry<String, Integer> entry : successMap.entrySet()) {
            if (entry.getValue() > max_value) {
                max_value = entry.getValue();
                result = entry.getKey();
            }
        }
        
        app.log(log);
        
        return result;
    }
    
    /**
     * Builds a list of split indexes from splitDuration.
     * @param series the time series to get split begin indexes of
     * @param splitDuration the duration between 2 splits
     * @return a list of begin indexes after split
     */
    public static List<Integer> getSplitBeginIndexes(TimeSeries series, Duration splitDuration) {
        ArrayList<Integer> beginIndexes = new ArrayList<>();

        int beginIndex = series.getBeginIndex();
        int endIndex = series.getEndIndex();
        
        // Adding the first begin index
        beginIndexes.add(beginIndex);

        // Building the first interval before next split
        ZonedDateTime beginInterval = series.getFirstBar().getEndTime();
        ZonedDateTime endInterval = beginInterval.plus(splitDuration);

        for (int i = beginIndex; i <= endIndex; i++) {
            // For each tick...
            ZonedDateTime tickTime = series.getBar(i).getEndTime();
            if (tickTime.isBefore(beginInterval) || !tickTime.isBefore(endInterval)) {
                // Tick out of the interval
                if (!endInterval.isAfter(tickTime)) {
                    // Tick after the interval
                    // --> Adding a new begin index
                    beginIndexes.add(i);
                }

                // Building the new interval before next split
                beginInterval = endInterval.isBefore(tickTime) ? tickTime : endInterval;
                endInterval = beginInterval.plus(splitDuration);
            }
        }
        return beginIndexes;
    }
    
    /**
     * Returns a new time series which is a view of a subset of the current series.
     * <p>
     * The new series has begin and end indexes which correspond to the bounds of the sub-set into the full series.<br>
     * The tick of the series are shared between the original time series and the returned one (i.e. no copy).
     * @param series the time series to get a sub-series of
     * @param beginIndex the begin index (inclusive) of the time series
     * @param duration the duration of the time series
     * @return a constrained {@link TimeSeries time series} which is a sub-set of the current series
     */
    public static TimeSeries subseries(TimeSeries series, int beginIndex, Duration duration) {

        // Calculating the sub-series interval
        ZonedDateTime beginInterval = series.getBar(beginIndex).getEndTime();
        ZonedDateTime endInterval = beginInterval.plus(duration);

        // Checking ticks belonging to the sub-series (starting at the provided index)
        int subseriesNbTicks = 0;
        int endIndex = series.getEndIndex();
        for (int i = beginIndex; i <= endIndex; i++) {
            // For each tick...
            ZonedDateTime tickTime = series.getBar(i).getEndTime();
            if (tickTime.isBefore(beginInterval) || !tickTime.isBefore(endInterval)) {
                // Tick out of the interval
                break;
            }
            // Tick in the interval
            // --> Incrementing the number of ticks in the subseries
            subseriesNbTicks++;
        }

        return new BaseTimeSeries(series, beginIndex, beginIndex + subseriesNbTicks - 1);
    }

    /**
     * Splits the time series into sub-series lasting sliceDuration.<br>
     * The current time series is splitted every splitDuration.<br>
     * The last sub-series may last less than sliceDuration.
     * @param series the time series to split
     * @param splitDuration the duration between 2 splits
     * @param sliceDuration the duration of each sub-series
     * @return a list of sub-series
     */
    public static List<TimeSeries> splitSeries(TimeSeries series, Duration splitDuration, Duration sliceDuration) {
        ArrayList<TimeSeries> subseries = new ArrayList<>();
        if (splitDuration != null && !splitDuration.isZero() && 
            sliceDuration != null && !sliceDuration.isZero()) {

            List<Integer> beginIndexes = getSplitBeginIndexes(series, splitDuration);
            for (Integer subseriesBegin : beginIndexes) {
                subseries.add(subseries(series, subseriesBegin, sliceDuration));
            }
        }
        return subseries;
    }
    
    private void tryStartSellUpSeq() {
        int i, imax = -1;
        long max_buy_time = 0;
        
        List<Trade> myTrades = client.getMyTrades(symbol);
        for (i=0; i < myTrades.size(); i++) {
            if (myTrades.get(i).isBuyer() && max_buy_time < myTrades.get(i).getTime()) {
                max_buy_time = myTrades.get(i).getTime();
                imax = i;
            }
        }
        if (imax >= 0) {
            float lastBuyPrice = Float.parseFloat(myTrades.get(imax).getPrice());
            float lastBuyQty = Float.parseFloat(myTrades.get(imax).getQty());
            
            AssetBalance qbalance = profitsChecker.getAccount().getAssetBalance(baseAssetSymbol);
            float free_cnt = Float.parseFloat(qbalance.getFree());
            //float locked_cnt = Float.parseFloat(qbalance.getLocked());
            float order_cnt = 0;
            
            orderToCancelOnSellUp.clear();
            
            if (free_cnt == 0 || sellUpAll) {
                List<Order> openOrders = client.getOpenOrders(new OrderRequest(symbol));
                for (i=0; i < openOrders.size(); i++) {
                    if (openOrders.get(i).getStatus() == OrderStatus.NEW && openOrders.get(i).getSide() == OrderSide.SELL) {
                        orderToCancelOnSellUp.add(openOrders.get(i).getOrderId());
                        order_cnt += Float.parseFloat(openOrders.get(i).getOrigQty());
                    }
                }
            }
            
            if (free_cnt > 0 || (order_cnt > 0 && !orderToCancelOnSellUp.isEmpty())) {
                float res_cnt = free_cnt + order_cnt;
                base_strategy_sell_ignored = true;
                sold_price = lastBuyPrice;
                sold_amount = res_cnt;
                app.log("START WAITING to sell " + df8.format(sold_amount) + " " + baseAssetSymbol + " for price more than " + df8.format(lastBuyPrice) + " " + quoteAssetSymbol, true, true);
                long result = profitsChecker.Buy(symbol, sold_amount, lastBuyPrice, true);
                if (result >= 0) {
                    limitOrderId = result;
                    app.log("Successful waiting start!", true, true);
                    is_hodling = true;
                    int endIndex = series.getEndIndex();
                    float curPrice = series.getBar(endIndex).getClosePrice().floatValue();
                    profitsChecker.setPairPrice(symbol, curPrice);
                } else {
                    app.log("Error in Buy method!", true, true);
                }
                return;
            }
        }
        isTryingToSellUp = false;
        app.log("Can't set SellUp mode for " + symbol, true, true);
    } 
    
    private void resetSeries() {
        series = new BaseTimeSeries(symbol + "_SERIES");
        need_bar_reset = false;
        List<Candlestick> bars = client.getCandlestickBars(symbol, barInterval, 2000, System.currentTimeMillis() - 2000 * barSeconds * 1000, System.currentTimeMillis());
        /*if (bars.size() >= 2) {
            long period = bars.get(bars.size()-1).getCloseTime() - bars.get(0).getOpenTime();
            List<Candlestick> bars_pre = client.getCandlestickBars(symbol, barInterval, 1000, bars.get(0).getOpenTime() - period - 1000, bars.get(bars.size()-1).getCloseTime() - period - 1000);
            if (bars_pre.size() > 0) {
                addBars(bars_pre);
            }
        }*/
        if (bars.size() > 0) {
            addBars(bars);
            last_time = bars.get(bars.size() - 1).getOpenTime();
        }
    }
    private void nextBars() {
        List<Candlestick> bars = null;
        if (need_bar_reset) {
            resetSeries();
            resetStrategies();
        } else {
            bars = client.getCandlestickBars(symbol, barInterval, 500, last_time, last_time + barSeconds * 2000);
            addBars(bars);
        }
        if (bars != null && bars.size() > 0) {
            last_time = bars.get(bars.size() - 1).getOpenTime();
            //app.log(symbol + ": price=" + bars.get(bars.size() - 1).getClose() + "; volume=" + bars.get(bars.size() - 1).getVolume() + "; bars=" + series.getBarCount(), false, true);
        }
    }
    
    @Override
    public void run(){  
        int exceptions_cnt = 0;

        if (!buyOnStart) {
            doWait(ThreadLocalRandom.current().nextLong(100, 500));
        }

        app.log("thread for " + symbol + " running...");
        app.log("");
        
        if (!buyOnStart) {
            doWait(startDelayTime);
        }
        
        try {
            ExchangeInfo info = client.getExchangeInfo();
            SymbolInfo pair_sinfo = info.getSymbolInfo(symbol);
            baseAssetSymbol = pair_sinfo.getBaseAsset();
            quoteAssetSymbol = pair_sinfo.getQuoteAsset();

            List<SymbolFilter> filters = pair_sinfo.getFilters();        
            filters.forEach((filter)->{
                if (null != filter.getFilterType()) switch (filter.getFilterType()) {
                    case PRICE_FILTER:
                        filterPrice = true;
                        filterMinPrice = Float.parseFloat(filter.getMinPrice());
                        filterMaxPrice = Float.parseFloat(filter.getMaxPrice());
                        filterPriceTickSize = Float.parseFloat(filter.getTickSize());
                        break;
                    case LOT_SIZE:
                        filterQty = true;
                        filterQtyStep = Float.parseFloat(filter.getStepSize());
                        filterMinQty = Float.parseFloat(filter.getMinQty());
                        filterMaxQty = Float.parseFloat(filter.getMaxQty());
                        break;
                    case MIN_NOTIONAL:
                        filterNotional = true;
                        filterMinNotional = Float.parseFloat(filter.getMinNotional());
                        break;
                    default:
                        break;
                }
            });
        } catch (Exception e) {
            app.log("symbol " + symbol + " error. Exiting from thread...");
            return;
        }

        baseStartBalance = getBaseBalance();
        quoteStartBalance = getQuoteBalance();
        
        profitsChecker.placeOrUpdatePair(baseAssetSymbol, quoteAssetSymbol, symbol, true);

        app.log(symbol + " filters:");
        if (filterPrice) {
            app.log("Price: min="+df8.format(filterMinPrice)+"; max="+df8.format(filterMaxPrice)+"; tick=" + df8.format(filterPriceTickSize));
        }
        if (filterQty) {
            app.log("Quantity: min="+df8.format(filterMinQty)+"; max="+df8.format(filterMaxQty)+"; step=" + df8.format(filterQtyStep));
        }
        if (filterNotional) {
            app.log("Notional: " + df8.format(filterMinNotional));
        }
        app.log("");

        if (!buyOnStart) {
            doWait(startDelayTime);
        }
        
        // https://github.com/ta4j/ta4j/blob/master/ta4j-examples/src/main/java/ta4jexamples/loaders/CsvTradesLoader.java
        // https://github.com/ta4j/ta4j/blob/master/ta4j-examples/src/main/java/ta4jexamples/bots/TradingBotOnMovingTimeSeries.java
        
        // autoselect strategy
        // https://github.com/mdeverdelhan/ta4j-origins/blob/master/ta4j-examples/src/main/java/ta4jexamples/walkforward/WalkForward.java
        
        
        resetSeries();

        /*
        System.out.println(bars.get(bars.size()-1));
        */
        
        
        /*if (bars.size() > 0) {
            app.log(symbol + ": price=" + bars.get(bars.size() - 1).getClose() + "; volume=" + bars.get(bars.size() - 1).getVolume() + "; bars=" + series.getBarCount(), false, true);
        }*/
        
        resetStrategies();
        
        if (isTryingToSellUp) {
            tryStartSellUpSeq();
        }
        
        currencyItem quote = profitsChecker.getProfitData(quoteAssetSymbol);
        if (quote != null) {
            baseStartBalance = quote.getInitialValue();
        }
        
        if (buyOnStart) {
            doBuy();
        }
        
        while (!need_stop) {
            if (paused) {
                doWait(12000);
                continue;
            }
            try { 
                nextBars();
                if (limitOrderId > 0) {
                    checkOrder();
                } else {
                    checkStatus();
                }
            } catch(BinanceApiException binex) {
                app.log("");
                app.log("EXCEPTION: " + binex.getLocalizedMessage(), false, true);
                exceptions_cnt++;
                if (exceptions_cnt > 3) {
                    break;
                }
                doWait(30000);
                continue;
            }
            doWait(delayTime * 1000);
            exceptions_cnt = 0;
        }
        app.log("");
        app.log("thread for " + symbol + " is stopped...");
    }

    /**
     * @return the symbol
     */
    public String getSymbol() {
        return symbol;
    }
    public String getBaseSymbol() {
        return baseAssetSymbol;
    }
    public boolean isHodling() {
        return is_hodling;
    }

    public void doBuy() {
        if (!is_hodling && limitOrderId == 0) {
            int endIndex = series.getEndIndex();
            float curPrice = series.getBar(endIndex).getClosePrice().floatValue();
            this.doEnter(curPrice);
        }
    }

    public void doSell() {
        if (is_hodling && limitOrderId == 0) {
            int endIndex = series.getEndIndex();
            float curPrice = series.getBar(endIndex).getClosePrice().floatValue();
            this.doExit(curPrice, true);
        }
    }

    public void doLimitCancel() {
        if (limitOrderId > 0) {
            client.cancelOrder(new CancelOrderRequest(symbol, limitOrderId));
            doWait(1000);
            checkOrder();
        }
    }
    
    public void doShowPlot() {
        plot = new CurrencyPlot(symbol, series, barSeconds);
        for(int i=0; i<markers.size(); i++) {
            plot.addMarker(markers.get(i).label, markers.get(i).timeStamp, markers.get(i).typeIndex);
        }
        plot.showPlot();
    }
    
    /**
     * @return the isTryingToSellUp
     */
    public boolean isTryingToSellUp() {
        return isTryingToSellUp;
    }

    /**
     * @param isTryingToSellUp the isTryingToSellUp to set
     */
    public void setTryingToSellUp(boolean isTryingToSellUp) {
        this.isTryingToSellUp = isTryingToSellUp;
    }

    /**
     * @return the isTryingToBuyDip
     */
    public boolean isTryingToBuyDip() {
        return isTryingToBuyDip;
    }

    /**
     * @param isTryingToBuyDip the isTryingToBuyDip to set
     */
    public void setTryingToBuyDip(boolean isTryingToBuyDip) {
        this.isTryingToBuyDip = isTryingToBuyDip;
    }

    /**
     * @return the sellUpAll
     */
    public boolean isSellUpAll() {
        return sellUpAll;
    }

    /**
     * @param sellUpAll the sellUpAll to set
     */
    public void setSellUpAll(boolean sellUpAll) {
        this.sellUpAll = sellUpAll;
    }

    /**
     * @return the do_remove_flag
     */
    public boolean is_do_remove_flag() {
        return do_remove_flag;
    }

    /**
     * @param do_remove_flag the do_remove_flag to set
     */
    public void set_do_remove_flag(boolean do_remove_flag) {
        this.do_remove_flag = do_remove_flag;
    }

    /**
     * @return the tradingBalancePercent
     */
    public float getTradingBalancePercent() {
        return tradingBalancePercent;
    }

    /**
     * @param tradingBalancePercent the tradingBalancePercent to set
     */
    public void setTradingBalancePercent(float tradingBalancePercent) {
        this.tradingBalancePercent = tradingBalancePercent;
    }

    /**
     * @return the mainStrategy
     */
    public String getMainStrategy() {
        return mainStrategy;
    }

    /**
     * @param mainStrategy the mainStrategy to set
     */
    public void setMainStrategy(String mainStrategy) {
        this.mainStrategy = mainStrategy;
        need_bar_reset = true;
    }
    
    /**
     * @param _barInterval the barInterval to set
     */
    public void setBarInterval(String _barInterval) {
        int newSeconds = barSeconds;
        if ("1m".equals(_barInterval)) {
            barInterval = CandlestickInterval.ONE_MINUTE;
            newSeconds = 60;
        } else if ("5m".equals(_barInterval)) {
            barInterval = CandlestickInterval.FIVE_MINUTES;
            newSeconds = 60 * 5;
        } else if ("15m".equals(_barInterval)) {
            barInterval = CandlestickInterval.FIFTEEN_MINUTES;
            newSeconds = 60 * 15;
        } else if ("30m".equals(_barInterval)) {
            barInterval = CandlestickInterval.HALF_HOURLY;
            newSeconds = 60 * 30;
        } else if ("1h".equals(_barInterval)) {
            barInterval = CandlestickInterval.HOURLY;
            newSeconds = 60 * 60;
        }
        if (newSeconds != barSeconds) {
            barSeconds = newSeconds;
            if (series != null && series.getBarCount() > 0) {
                need_bar_reset = true;
            }
        }
    }

    /**
     * @return the delayTime
     */
    public long getDelayTime() {
        return delayTime;
    }

    /**
     * @param delayTime the delayTime to set
     */
    public void setDelayTime(long delayTime) {
        this.delayTime = delayTime;
    }

    /**
     * @return the lowHold
     */
    public boolean isLowHold() {
        return lowHold;
    }

    /**
     * @param lowHold the lowHold to set
     */
    public void setLowHold(boolean lowHold) {
        this.lowHold = lowHold;
    }

    void setBuyOnStart(boolean buyOnStart) {
        this.buyOnStart = buyOnStart;
    }

    void setStartDelay(int startDelayTime) {
        this.startDelayTime = startDelayTime;
    }

    void setCheckOtherStrategies(boolean checkOtherStrategies) {
        this.checkOtherStrategies = checkOtherStrategies;
    }
}
