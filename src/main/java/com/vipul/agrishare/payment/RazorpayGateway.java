package com.vipul.agrishare.payment;

import com.vipul.agrishare.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

/**
 * Razorpay Orders API with manual capture: the renter's money is only
 * authorized at checkout and captured when the owner approves. Authorized
 * payments that are never captured (rejected/expired) are auto-refunded by Razorpay.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.payments.mode", havingValue = "razorpay")
public class RazorpayGateway implements PaymentGateway {

    private static final String BASE_URL = "https://api.razorpay.com";

    private final RestClient client;
    private final String keyId;
    private final String keySecret;

    public RazorpayGateway(PaymentProperties properties, RestClient.Builder builder) {
        PaymentProperties.Razorpay razorpay = properties.razorpay();
        if (razorpay == null || !StringUtils.hasText(razorpay.keyId()) || !StringUtils.hasText(razorpay.keySecret())) {
            throw new IllegalStateException(
                    "app.payments.mode=razorpay needs RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET env vars");
        }
        this.keyId = razorpay.keyId();
        this.keySecret = razorpay.keySecret();
        String basic = Base64.getEncoder()
                .encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
        this.client = builder
                .baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .build();
    }

    @Override
    public String mode() {
        return "razorpay";
    }

    @Override
    public String publicKeyId() {
        return keyId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public GatewayOrder createOrder(long amountPaise, String receipt) {
        Map<String, Object> body = Map.of(
                "amount", amountPaise,
                "currency", "INR",
                "receipt", receipt,
                "payment", Map.of("capture", "manual")
        );
        Map<String, Object> response = call(() -> client.post()
                .uri("/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class));
        return new GatewayOrder((String) response.get("id"), amountPaise, "INR");
    }

    @Override
    public boolean verifySignature(String orderId, String paymentId, String signature) {
        if (orderId == null || paymentId == null || signature == null) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8));
            byte[] given = signature.getBytes(StandardCharsets.UTF_8);
            // constant-time comparison so the signature can't be guessed byte by byte
            return MessageDigest.isEqual(HexFormat.of().formatHex(expected).getBytes(StandardCharsets.UTF_8), given);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    @Override
    public void capture(String paymentId, long amountPaise) {
        call(() -> client.post()
                .uri("/v1/payments/{id}/capture", paymentId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("amount", amountPaise, "currency", "INR"))
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public void refund(String paymentId, long amountPaise) {
        call(() -> client.post()
                .uri("/v1/payments/{id}/refund", paymentId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("amount", amountPaise))
                .retrieve()
                .toBodilessEntity());
    }

    private <T> T call(java.util.function.Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientException e) {
            log.error("Razorpay call failed", e);
            throw new ApiException("error.payment.gateway", HttpStatus.BAD_GATEWAY);
        }
    }
}
