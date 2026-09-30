package com.vipul.agrishare.repository;

import com.vipul.agrishare.entity.Review;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByBookingIdAndReviewerId(Long bookingId, Long reviewerId);

    @Query("select r.booking.id from Review r where r.reviewer.id = :reviewerId and r.booking.id in :bookingIds")
    List<Long> findReviewedBookingIds(@Param("reviewerId") Long reviewerId,
                                      @Param("bookingIds") Collection<Long> bookingIds);

    @EntityGraph(attributePaths = {"reviewer", "equipment"})
    List<Review> findByEquipmentIdAndReviewerRoleOrderByCreatedAtDesc(Long equipmentId, Review.ReviewerRole role,
                                                                      Pageable pageable);

    @EntityGraph(attributePaths = {"reviewer", "equipment"})
    List<Review> findByRevieweeIdOrderByCreatedAtDesc(Long revieweeId, Pageable pageable);

    /** Machine rating = what renters said. Rows: [equipmentId, avg, count]. */
    @Query("""
            select r.equipment.id, avg(r.rating), count(r) from Review r
            where r.equipment.id in :ids and r.reviewerRole = com.vipul.agrishare.entity.Review.ReviewerRole.RENTER
            group by r.equipment.id
            """)
    List<Object[]> equipmentRatings(@Param("ids") Collection<Long> equipmentIds);

    /** Farmer rating = everything said about them, as owner or renter. Rows: [userId, avg, count]. */
    @Query("""
            select r.reviewee.id, avg(r.rating), count(r) from Review r
            where r.reviewee.id in :ids
            group by r.reviewee.id
            """)
    List<Object[]> userRatings(@Param("ids") Collection<Long> userIds);
}
