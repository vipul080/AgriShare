package com.vipul.agrishare.payment;

import com.vipul.agrishare.exception.ApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * PAYMENTS_MODE=off: cash only. The UI hides online payment and boosts, and the server
 * refuses them too, so a real deployment can never show the mock "pretend payment" screen.
 */
@Component
@ConditionalOnProperty(name = "app.payments.mode", havingValue = "off")
public class DisabledPaymentGateway implements PaymentGateway {

    public static final String MODE = "off";

    @Override
    public String mode() {
        return MODE;
    }

    @Override
    public String publicKeyId() {
        return null;
    }

    @Override
    public GatewayOrder createOrder(long amountPaise, String receipt) {
        throw ApiException.badRequest("error.payment.disabled");
    }

    @Override
    public boolean verifySignature(String orderId, String paymentId, String signature) {
        return false;
    }

    @Override
    public void capture(String paymentId, long amountPaise) {
        throw ApiException.badRequest("error.payment.disabled");
    }

    @Override
    public void refund(String paymentId, long amountPaise) {
        throw ApiException.badRequest("error.payment.disabled");
    }
}
