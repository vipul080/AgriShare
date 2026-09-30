package com.vipul.agrishare.push;

import java.util.Map;

/** Delivers a phone push notification. FCM implementation comes with the Android app. */
public interface PushSender {

    void send(String deviceToken, String title, String body, Map<String, String> data);
}
