package com.vipul.agrishare.payment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Simulated gateway for local runs and demos: no money moves. The UI's mock
 * checkout sends back a "pay_mock_*" id with signature "mock".
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.payments.mode", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    static final String MOCK_SIGNATURE = "mock";

    @Override
    public String mode() {
        return "mock";
    }

    @Override
    public String publicKeyId() {
        return null;
    }

    @Override
    public GatewayOrder createOrder(long amountPaise, String receipt) {
        String orderId = "order_mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        log.info("[mock-payments] order {} for {} paise ({})", orderId, amountPaise, receipt);
        return new GatewayOrder(orderId, amountPaise, "INR");
    }

    @Override
    public boolean verifySignature(String orderId, String paymentId, String signature) {
        return orderId != null && orderId.startsWith("order_mock_")
                && paymentId != null && paymentId.startsWith("pay_mock_")
                && MOCK_SIGNATURE.equals(signature);
    }

    @Override
    public void capture(String paymentId, long amountPaise) {
        log.info("[mock-payments] captured {} ({} paise)", paymentId, amountPaise);
    }

    @Override
    public void refund(String paymentId, long amountPaise) {
        log.info("[mock-payments] refunded {} ({} paise)", paymentId, amountPaise);
    }
}
