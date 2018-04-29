/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot.coinrating;

/**
 *
 * @author EVG_adm_T
 */
public class CoinRatingLogItem {
    public String symbol;
    public String fullname;

    public int rank = 0;
    public float market_cap = 0;
    public int events_count = 0;

    public boolean do_remove_flag = false;

    public Float sort = 0f;
    public int update_counter = 0;

    public String last_event_date;
    public long last_event_anno_millis = 0;
    public long last_rating_update_millis = 0;

    public float rating = 0;
    public float rating_inc = 0;
    
    public void calculateRating() {
        rating = 0;
        rating += events_count / 20;
        if (last_event_anno_millis < 1*60*60*1000) {
            rating++;
        }
        if (last_event_anno_millis < 24*60*60*1000) {
            rating++;
        }
        if (market_cap > 20000000) {
            rating += 0.2 * Math.pow(market_cap / 20000000, 0.333);
        }
        rating += rating_inc;
    }
}
