package com.satori.android_demo;

import android.location.Location;

/**
 * Created by sseeley on 6/17/17.
 */

public class ChatMessage {
    String user;
    String text;
    double lat;
    double lon;
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
            this.lat = loc.getLatitude();
            this.lon = loc.getLongitude();
        }
        this.tag = tag;
    }
}