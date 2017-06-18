package com.satori.android_demo;

import android.location.Location;
import android.test.SyncBaseInstrumentation;
import android.text.style.SubscriptSpan;

/**
 * Created by sseeley on 6/17/17.
 */

public class SubscriptionChangeMessage {
    public String tag;
    public String lat;
    public String lon;

    public SubscriptionChangeMessage(String tag, Location loc){
        this.tag = tag;
        if(loc != null) {
            this.lat = Double.toString(loc.getLatitude());
            this.lon = Double.toString(loc.getLongitude());
        }
    }

    public SubscriptionChangeMessage(String tag){
        this.tag = tag;
    }

    public SubscriptionChangeMessage(Location loc){
        this.lat = Double.toString(loc.getLatitude());
        this.lon = Double.toString(loc.getLongitude());
    }
}
