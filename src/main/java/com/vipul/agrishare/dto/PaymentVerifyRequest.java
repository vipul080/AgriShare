package com.vipul.agrishare.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

/** Fields returned by the checkout widget; accepts Razorpay's snake_case names as-is. */
public record PaymentVerifyRequest(

        @NotBlank(message = "{validation.payment.fields.required}")
        @JsonAlias("razorpay_order_id")
        String orderId,

        @NotBlank(message = "{validation.payment.fields.required}")
        @JsonAlias("razorpay_payment_id")
        String paymentId,

        @NotBlank(message = "{validation.payment.fields.required}")
        @JsonAlias("razorpay_signature")
        String signature
) {}
