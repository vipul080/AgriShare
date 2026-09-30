package com.vipul.agrishare.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record BookingResponse(
        Long id,
        EquipmentBrief equipment,
        UserSummary renter,
        UserSummary owner,
        LocalDate startDate,
        LocalDate endDate,
        int days,
        BigDecimal pricePerDay,
        BigDecimal totalAmount,
        /** Online service fee (0 for cash); the renter pays totalAmount + platformFee. */
        BigDecimal platformFee,
        BigDecimal amountPayable,
        String status,
        String paymentMethod,
        String paymentStatus,
        String note,
        String rejectReason,
        /** Only filled once CONFIRMED, so both farmers can call each other. */
        String renterPhone,
        String ownerPhone,
        /** Present while an ONLINE booking still needs the renter to pay. */
        Checkout checkout,
        /** COMPLETED and the viewer hasn't reviewed it yet => show "Rate". */
        boolean reviewedByMe,
        Instant createdAt
) {
    public record EquipmentBrief(Long id, String name, String category, String imageUrl, String address) {}

    public record Checkout(String mode, String keyId, String orderId, long amountPaise, String currency) {}
}
