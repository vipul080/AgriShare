package com.vipul.agrishare.dto;

import java.time.Instant;

/** The signed-in farmer's own profile — includes contact details, unlike UserSummary. */
public record ProfileResponse(
        Long id,
        String name,
        String phone,
        String email,
        String role,
        Double latitude,
        Double longitude,
        String address,
        String preferredLanguage,
        Double averageRating,
        long reviewCount,
        Instant createdAt
) {}
