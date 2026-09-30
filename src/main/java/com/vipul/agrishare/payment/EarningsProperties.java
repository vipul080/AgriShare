package com.vipul.agrishare.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * How the platform earns. Both levers are off by default so early adoption isn't hurt.
 * Rule: cash bookings are never charged anything extra; every fee is shown before paying.
 */
@ConfigurationProperties(prefix = "app.earnings")
public record EarningsProperties(
        /** % added to ONLINE booking payments only. 0 = off. */
        BigDecimal onlineFeePercent,
        /** Upper limit of that fee in rupees, so big rentals don't get a big fee. */
        BigDecimal onlineFeeCap,
        Boost boost
) {
    public record Boost(boolean enabled, BigDecimal price, int days) {}

    public EarningsProperties {
        if (onlineFeePercent == null) onlineFeePercent = BigDecimal.ZERO;
        if (onlineFeeCap == null) onlineFeeCap = BigDecimal.ZERO;
        if (boost == null) boost = new Boost(false, new BigDecimal("49"), 7);
    }

    /** Whole rupees, rounded half-up, never above the cap. Zero when the fee is off. */
    public BigDecimal onlineFee(BigDecimal rentTotal) {
        if (onlineFeePercent.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal fee = rentTotal.multiply(onlineFeePercent)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        return onlineFeeCap.signum() > 0 ? fee.min(onlineFeeCap) : fee;
    }
}
