/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.coinrating;

import com.evgcompany.binntrdbot.tradePairProcess;

/**
 *
 * @author EVG_adm_T
 */
public class CoinRatingPairLogItem {
    public String symbol;
    public String symbolQuote;
    public tradePairProcess pair = null;
    public CoinRatingLogItem base_rating = new CoinRatingLogItem();

    public Float percent_hour = 0f;
    public Float percent_day = 0f;
    public Float percent_from_begin = 0f;

    public Float start_price = 0f;
    public Float hour_ago_price = 0f;
    public Float day_ago_price = 0f;
    public Float current_price = 0f;

    public Float hour_volume = 0f;
    public Float day_volume = 0f;
    public Float volatility = 0f;
    
    public Float signal_rating = 0f;

    public boolean do_remove_flag = false;
    public boolean fastbuy_skip = false;

    public Float sort = 0f;
    public int update_counter = 0;

    public long last_rating_update_millis = 0;
    public double strategies_shouldenter_rate = 0;
    public double strategies_shouldexit_rate = 0;

    public float rating = 0;
    public float rating_inc = 0;
    
    public void calculateRating() {
        rating = 0;
        rating += base_rating.events_count / 20;
        rating += volatility * 150;
        if (base_rating.last_event_anno_millis < 1*60*60*1000) {
            rating++;
        }
        if (base_rating.last_event_anno_millis < 24*60*60*1000) {
            rating++;
        }
        if (base_rating.market_cap > 20000000) {
            rating += 0.2 * Math.pow(base_rating.market_cap / 20000000, 0.333);
        }
        if (hour_ago_price > 0 && hour_ago_price < current_price) {
            rating += current_price / hour_ago_price;
        }
        if (day_ago_price > 0 && day_ago_price < current_price) {
            rating += current_price / day_ago_price;
        }
        if (strategies_shouldenter_rate > strategies_shouldexit_rate) {
            rating+=2;
        }
        if (strategies_shouldenter_rate > strategies_shouldexit_rate + 4) {
            rating++;
        }
        if (strategies_shouldenter_rate > strategies_shouldexit_rate + 10) {
            rating++;
        }
        if (hour_volume > 0.1 * day_volume) {
            rating++;
        }
        if (hour_volume > 0.25 * day_volume) {
            rating++;
        }
        rating += signal_rating * 1.25;
        rating += rating_inc;
    }
}
