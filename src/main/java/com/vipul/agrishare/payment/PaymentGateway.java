package com.vipul.agrishare.payment;

/**
 * Two-step (authorize now, capture on owner approval) payment provider.
 * Amounts are in paise — the smallest INR unit — as gateways expect.
 */
public interface PaymentGateway {

    /** "mock" or "razorpay"; the UI switches checkout flows on this. */
    String mode();

    /** Public key the browser/Android checkout needs; null for mock. */
    String publicKeyId();

    GatewayOrder createOrder(long amountPaise, String receipt);

    /** Checks the checkout callback really came from the gateway for this order. */
    boolean verifySignature(String orderId, String paymentId, String signature);

    void capture(String paymentId, long amountPaise);

    void refund(String paymentId, long amountPaise);

    record GatewayOrder(String orderId, long amountPaise, String currency) {}
}
