package com.vipul.agrishare.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payments")
public record PaymentProperties(
        String mode,
        Razorpay razorpay,
        int authorizationExpiryHours,
        int checkoutTimeoutMinutes
) {
    public record Razorpay(String keyId, String keySecret) {}
}
