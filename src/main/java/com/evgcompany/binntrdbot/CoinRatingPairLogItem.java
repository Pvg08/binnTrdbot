/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

/**
 *
 * @author EVG_adm_T
 */
public class CoinRatingPairLogItem {
    String symbol;
    String fullname;
    tradePairProcess pair = null;

    int rank = 0;
    float market_cap = 0;
    int events_count = 0;

    Float percent_hour = 0f;
    Float percent_day = 0f;
    Float percent_from_begin = 0f;

    Float start_price = 0f;
    Float hour_ago_price = 0f;
    Float day_ago_price = 0f;
    Float current_price = 0f;

    Float hour_volume = 0f;
    Float day_volume = 0f;
    Float volatility = 0f;
    
    Float signal_rating = 0f;

    boolean do_remove_flag = false;
    boolean fastbuy_skip = false;

    Float sort = 0f;
    int update_counter = 0;

    String last_event_date;
    long last_event_anno_millis = 0;
    long last_rating_update_millis = 0;
    double strategies_shouldenter_rate = 0;
    double strategies_shouldexit_rate = 0;

    float rating = 0;
    float rating_inc = 0;
    
    public void calculateRating() {
        rating = 0;
        rating += events_count / 20;
        rating += volatility * 150;
        if (last_event_anno_millis < 1*60*60*1000) {
            rating++;
        }
        if (last_event_anno_millis < 24*60*60*1000) {
            rating++;
        }
        if (market_cap > 20000000) {
            rating += 0.2 * Math.pow(market_cap / 20000000, 0.333);
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
