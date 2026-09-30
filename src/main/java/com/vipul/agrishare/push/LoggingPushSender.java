package com.vipul.agrishare.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Default until FCM credentials exist: just logs what would have been pushed. */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.push.mode", havingValue = "log", matchIfMissing = true)
public class LoggingPushSender implements PushSender {

    @Override
    public void send(String deviceToken, String title, String body, Map<String, String> data) {
        log.info("[push] to …{}: {} — {} {}", tail(deviceToken), title, body, data);
    }

    private static String tail(String token) {
        return token.length() <= 6 ? token : token.substring(token.length() - 6);
    }
}
