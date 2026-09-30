package com.vipul.agrishare.controller;

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

    /** Tells the UI which checkout to load: the mock dialog or Razorpay's widget with this key. */
    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> config = new HashMap<>();
        config.put("mode", paymentGateway.mode());
        config.put("keyId", paymentGateway.publicKeyId());
        config.put("currency", "INR");
        return config;
    }
}
