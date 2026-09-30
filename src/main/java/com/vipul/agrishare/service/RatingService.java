package com.vipul.agrishare.service;

import com.vipul.agrishare.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ratings are computed on read from the reviews table (aggregate query)
 * rather than denormalised onto equipment/users — see README design notes.
 */
@Service
@RequiredArgsConstructor
public class RatingService {

    private final ReviewRepository reviewRepository;

    public record RatingSummary(Double average, long count) {
        public static final RatingSummary EMPTY = new RatingSummary(null, 0);
    }

    @Transactional(readOnly = true)
    public Map<Long, RatingSummary> forEquipment(Collection<Long> equipmentIds) {
        return equipmentIds.isEmpty() ? Map.of() : toMap(reviewRepository.equipmentRatings(equipmentIds));
    }

    @Transactional(readOnly = true)
    public Map<Long, RatingSummary> forUsers(Collection<Long> userIds) {
        return userIds.isEmpty() ? Map.of() : toMap(reviewRepository.userRatings(userIds));
    }

    public RatingSummary forUser(Long userId) {
        return forUsers(List.of(userId)).getOrDefault(userId, RatingSummary.EMPTY);
    }

    public RatingSummary forOneEquipment(Long equipmentId) {
        return forEquipment(List.of(equipmentId)).getOrDefault(equipmentId, RatingSummary.EMPTY);
    }

    private static Map<Long, RatingSummary> toMap(List<Object[]> rows) {
        Map<Long, RatingSummary> map = new HashMap<>();
        for (Object[] row : rows) {
            double avg = ((Number) row[1]).doubleValue();
            map.put((Long) row[0], new RatingSummary(Math.round(avg * 10) / 10.0, ((Number) row[2]).longValue()));
        }
        return map;
    }
}
