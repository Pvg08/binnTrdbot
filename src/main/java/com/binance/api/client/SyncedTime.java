package com.binance.api.client;

public class SyncedTime {
    private long offsetServerTime;
    private static SyncedTime instance;

    private SyncedTime(long offsetServerTime) {
        this.offsetServerTime = offsetServerTime;
    }

    public static SyncedTime getInstance(long serverTime) {
        if (instance != null) {
            return instance;
        }

        synchronized (SyncedTime.class) {
            // currentTime + offset = serverTime; So currentTime - serverTime = -offset
            long offsetTime = System.currentTimeMillis() - serverTime;
            instance = new SyncedTime(offsetTime);
            return instance;
        }
    }

    public long currentTimeMillis() {
        return System.currentTimeMillis() - offsetServerTime;
    }
}