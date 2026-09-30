package com.vipul.agrishare.controller;

import com.vipul.agrishare.payment.EarningsProperties;
import com.vipul.agrishare.payment.PaymentGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentGateway paymentGateway;
    private final EarningsProperties earnings;

    /**
     * Tells the UI which checkout to load (mock dialog or Razorpay with this key) and
     * which optional charges are switched on, so it can show them before anyone pays.
     */
    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> config = new HashMap<>();
        config.put("mode", paymentGateway.mode());
        config.put("keyId", paymentGateway.publicKeyId());
        config.put("currency", "INR");
        config.put("onlineFeePercent", earnings.onlineFeePercent());
        config.put("onlineFeeCap", earnings.onlineFeeCap());
        config.put("boost", Map.of(
                "enabled", earnings.boost().enabled(),
                "price", earnings.boost().price(),
                "days", earnings.boost().days()));
        return config;
    }
}
