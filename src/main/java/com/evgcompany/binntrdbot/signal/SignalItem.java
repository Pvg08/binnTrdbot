/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.signal;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 *
 * @author EVG_adm_T
 */
public class SignalItem {
    private String symbol1;
    private String symbol2;
    private String channelName;
    private BigDecimal price_from;
    private BigDecimal price_to;
    private BigDecimal price_target;
    private boolean done;
    private boolean timeout;
    private LocalDateTime datetime;
    private double rating;

    public long getMillisFromSignalStart() {
        long millis = System.currentTimeMillis() - datetime.toInstant(ZoneOffset.UTC).toEpochMilli();
        if (millis < 0) millis = 0;
        return millis;
    }
    
    /**
     * @return the pair
     */
    public String getPair() {
        return getSymbol1()+getSymbol2();
    }

    /**
     * @return the price_from
     */
    public BigDecimal getPriceFrom() {
        return price_from;
    }

    /**
     * @param price_from the price_from to set
     */
    public void setPriceFrom(BigDecimal price_from) {
        this.price_from = price_from;
    }

    /**
     * @return the price_to
     */
    public BigDecimal getPriceTo() {
        return price_to;
    }

    /**
     * @param price_to the price_to to set
     */
    public void setPriceTo(BigDecimal price_to) {
        this.price_to = price_to;
    }

    /**
     * @return the price_target
     */
    public BigDecimal getPriceTarget() {
        return price_target;
    }

    /**
     * @param price_target the price_target to set
     */
    public void setPriceTarget(BigDecimal price_target) {
        this.price_target = price_target;
    }

    /**
     * @return the active
     */
    public boolean isDone() {
        return done;
    }

    /**
     * @param done the active to set
     */
    public void setDone(boolean done) {
        this.done = done;
    }

    /**
     * @return the datetime
     */
    public LocalDateTime getDatetime() {
        return datetime;
    }

    /**
     * @param datetime the datetime to set
     */
    public void setDatetime(LocalDateTime datetime) {
        this.datetime = datetime;
    }

    /**
     * @return the symbol1
     */
    public String getSymbol1() {
        return symbol1;
    }

    /**
     * @param symbol1 the symbol1 to set
     */
    public void setSymbol1(String symbol1) {
        this.symbol1 = symbol1;
    }

    /**
     * @return the symbol2
     */
    public String getSymbol2() {
        return symbol2;
    }

    /**
     * @param symbol2 the symbol2 to set
     */
    public void setSymbol2(String symbol2) {
        this.symbol2 = symbol2;
    }

    /**
     * @return the rating
     */
    public double getRating() {
        return rating;
    }

    /**
     * @param rating the rating to set
     */
    public void setRating(double rating) {
        this.rating = rating;
    }

    /**
     * @return the timeout
     */
    public boolean isTimeout() {
        return timeout;
    }

    /**
     * @param timeout the timeout to set
     */
    public void setTimeout(boolean timeout) {
        this.timeout = timeout;
    }

    /**
     * @return the channelName
     */
    public String getChannelName() {
        return channelName;
    }

    /**
     * @param channelName the channelName to set
     */
    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }
}
