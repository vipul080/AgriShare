package com.vipul.agrishare.service;

import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;

/**
 * Ratings are computed on read from the reviews table (aggregate query)
 * rather than denormalised onto equipment/users — see README design notes.
 */
@Service
public class RatingService {

    public record RatingSummary(Double average, long count) {
        public static final RatingSummary EMPTY = new RatingSummary(null, 0);
    }

    public Map<Long, RatingSummary> forEquipment(Collection<Long> equipmentIds) {
        return Map.of(); // wired to ReviewRepository once reviews exist
    }
}
