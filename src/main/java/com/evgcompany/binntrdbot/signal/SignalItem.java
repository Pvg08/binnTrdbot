/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.signal;

import com.evgcompany.binntrdbot.coinrating.CoinRatingPairLogItem;
import com.evgcompany.binntrdbot.mainApplication;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;

/**
 *
 * @author EVG_adm_T
 */
public class SignalItem {
    private String symbol1;
    private String symbol2;
    private String channelName;
    private String hash;
    private BigDecimal price_from;
    private BigDecimal price_to;
    private BigDecimal price_target;
    private BigDecimal price_stoploss;
    private boolean price_from_auto;
    private boolean price_to_auto;
    private boolean price_target_auto;
    private boolean price_stoploss_auto;
    private boolean done;
    private boolean timeout;
    private boolean autopick = true;
    private LocalDateTime datetime;
    private long localMillis;
    private double rating;
    private long maxDaysAgo;
    
    private static final DecimalFormat df8 = new DecimalFormat("0.#######");

    public long getMillisFromSignalStart() {
        long millis = System.currentTimeMillis() - localMillis;
        if (millis < 0) millis = 0;
        return millis;
    }

    public void turnOffIfNeeded() {
        if (!isTimeout() && LocalDateTime.now().minusDays(maxDaysAgo).isAfter(getDatetime())) {
            setTimeout(true);
            setDone(true);
        }
    }

    public void updateDoneAndTimeout() {
        if (!timeout) turnOffIfNeeded();
        if (!done) {
            double cprice = tryGetCurrentPrice(null);
            done = cprice > price_target.doubleValue();
        }
    }
    
    public double tryGetCurrentPrice(BigDecimal default_price) {
        double result;
        if (default_price == null || default_price.compareTo(BigDecimal.ZERO) <= 0) {
            CoinRatingPairLogItem logItem = null;
            if (mainApplication.getInstance().getCoinRatingController() != null &&
                    mainApplication.getInstance().getCoinRatingController().isAlive() &&
                    !mainApplication.getInstance().getCoinRatingController().getCoinRatingMap().isEmpty()
            ) {
                logItem = mainApplication.getInstance().getCoinRatingController().getCoinRatingMap().get(getPair());
            }
            if (logItem != null) {
                result = logItem.current_price;
            } else {
                result = price_from.add(price_to).divide(BigDecimal.valueOf(2)).doubleValue();
            }
        } else {
            result = default_price.doubleValue();
        }
        return result;
    }
    
    public double getMedianProfitPercent() {
        return getPriceProfitPercent(null);
    }
    public double getPriceProfitPercent(BigDecimal price) {
        double median_price;
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            median_price = price_from.add(price_to).divide(BigDecimal.valueOf(2)).doubleValue();
        } else {
            median_price = price.doubleValue();
        }
        double target_price = price_target.doubleValue();
        if (target_price <= 0) {
            target_price = median_price;
        }
        return 100 * (target_price - median_price) / target_price;
    }

    public double getCurrentRating() {
        double millis_maxago = LocalDateTime.now().minusDays(maxDaysAgo).toInstant(ZoneOffset.UTC).toEpochMilli();
        double millis_dtime = localMillis;
        double millis_now = System.currentTimeMillis();
        double koef;
        double сrating;
        turnOffIfNeeded();
        if (!isTimeout()) {
            if (!isDone()) {
                сrating = getBaseRating() + 0.5;
                koef = 1.0 - (millis_now - millis_dtime) / (millis_now - millis_maxago);
                koef = koef * koef * koef;
                if (koef > 1) {
                    koef = 1;
                } else if (koef < 0.0025) {
                    koef = 0.0025;
                }
            } else {
                сrating = getBaseRating() / 2.0;
                koef = 0.0025;
            }
        } else {
            сrating = getBaseRating() / 10.0;
            koef = 0.0025;
        }
        return сrating * koef;
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
    public double getBaseRating() {
        return rating;
    }

    /**
     * @param rating the rating to set
     */
    public void setBaseRating(double rating) {
        this.rating = rating;
    }

    /**
     * @param rating the rating to set
     */
    public void mergeBaseRating(double rating) {
        if (this.rating > rating) {
            this.rating += rating / 2;
        } else {
            this.rating = rating + this.rating / 2;
        }
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

    /**
     * @return the localMillis
     */
    public long getLocalMillis() {
        return localMillis;
    }

    /**
     * @param localMillis the localMillis to set
     */
    public void setLocalMillis(long localMillis) {
        this.localMillis = localMillis;
    }

    /**
     * @return the maxDaysAgo
     */
    public long getMaxDaysAgo() {
        return maxDaysAgo;
    }

    /**
     * @param maxDaysAgo the maxDaysAgo to set
     */
    public void setMaxDaysAgo(long maxDaysAgo) {
        this.maxDaysAgo = maxDaysAgo;
    }

    /**
     * @return the price_stoploss
     */
    public BigDecimal getPriceStoploss() {
        return price_stoploss;
    }

    /**
     * @param price_stoploss the price_stoploss to set
     */
    public void setPriceStoploss(BigDecimal price_stoploss) {
        this.price_stoploss = price_stoploss;
    }

    /**
     * @return the price_from_auto
     */
    public boolean isPriceFromAuto() {
        return price_from_auto;
    }

    /**
     * @param price_from_auto the price_from_auto to set
     */
    public void setPriceFromAuto(boolean price_from_auto) {
        this.price_from_auto = price_from_auto;
    }

    /**
     * @return the price_to_auto
     */
    public boolean isPriceToAuto() {
        return price_to_auto;
    }

    /**
     * @param price_to_auto the price_to_auto to set
     */
    public void setPriceToAuto(boolean price_to_auto) {
        this.price_to_auto = price_to_auto;
    }

    /**
     * @return the price_target_auto
     */
    public boolean isPriceTargetAuto() {
        return price_target_auto;
    }

    /**
     * @param price_target_auto the price_target_auto to set
     */
    public void setPriceTargetAuto(boolean price_target_auto) {
        this.price_target_auto = price_target_auto;
    }

    /**
     * @return the price_stoploss_auto
     */
    public boolean isPriceStoplossAuto() {
        return price_stoploss_auto;
    }

    /**
     * @param price_stoploss_auto the price_stoploss_auto to set
     */
    public void setPriceStoplossAuto(boolean price_stoploss_auto) {
        this.price_stoploss_auto = price_stoploss_auto;
    }

    /**
     * @return the hash
     */
    public String getHash() {
        return hash;
    }

    public void updateHash() {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update(toString().getBytes());
            hash = new String(messageDigest.digest());
        } catch (NoSuchAlgorithmException ex) {
            hash = toString();
        }
    }
    
    @Override
    public String toString() {
        return symbol1+"_"
                +symbol2+"_"
                +df8.format(price_from)+"_"
                +df8.format(price_to)+"_"
                +df8.format(price_target)+"_"
                +df8.format(price_stoploss)+"_";
    }

    /**
     * @return the autopick
     */
    public boolean isAutopick() {
        return autopick;
    }

    /**
     * @param autopick the autopick to set
     */
    public void setAutopick(boolean autopick) {
        this.autopick = autopick;
    }
}
