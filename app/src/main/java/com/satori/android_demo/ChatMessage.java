package com.satori.android_demo;

import android.location.Location;

/**
 * Created by sseeley on 6/17/17.
 */

public class ChatMessage {
    String user;
    String text;
    String lat;
    String lon;
    String tag;

    ChatMessage() {
    }

    ChatMessage(String user, String text) {
        this.user = user;
        this.text = text;
    }

    ChatMessage(String user, String text, Location loc, String tag) {
        this.user = user;
        this.text = text;
        if(loc != null) {
            this.lat = Double.toString(loc.getLatitude());
            this.lon = Double.toString(loc.getLongitude());
        }
        this.tag = tag;
    }
}