package com.vipul.agrishare.dto;

import java.time.Instant;
import java.util.Map;

/**
 * title/body are pre-rendered in the request's Accept-Language (handy for Android);
 * type + params let the web UI re-render instantly when the user switches language.
 */
public record NotificationResponse(
        Long id,
        String type,
        Map<String, String> params,
        String title,
        String body,
        Long bookingId,
        boolean read,
        Instant createdAt
) {}
