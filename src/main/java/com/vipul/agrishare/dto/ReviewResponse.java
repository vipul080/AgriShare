package com.vipul.agrishare.dto;

import java.time.Instant;
import java.util.List;

public record ReviewResponse(
        Long id,
        int rating,
        String comment,
        UserSummary reviewer,
        String reviewerRole,
        Long equipmentId,
        String equipmentName,
        Instant createdAt
) {
    /** A rating header plus the latest reviews, for equipment and profile pages. */
    public record Page(Double averageRating, long reviewCount, List<ReviewResponse> reviews) {}
}
