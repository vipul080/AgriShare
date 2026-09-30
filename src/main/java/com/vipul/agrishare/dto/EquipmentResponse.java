package com.vipul.agrishare.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record EquipmentResponse(
        Long id,
        String name,
        String category,
        String description,
        BigDecimal pricePerDay,
        Double latitude,
        Double longitude,
        String address,
        String imageUrl,
        boolean available,
        UserSummary owner,
        Double distanceKm,
        Double averageRating,
        long reviewCount,
        Instant createdAt
) {}
