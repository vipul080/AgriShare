package com.vipul.agrishare.service;

import com.vipul.agrishare.dto.ReviewRequest;
import com.vipul.agrishare.dto.ReviewResponse;
import com.vipul.agrishare.dto.UserSummary;
import com.vipul.agrishare.entity.Booking;
import com.vipul.agrishare.entity.Review;
import com.vipul.agrishare.entity.User;
import com.vipul.agrishare.exception.ApiException;
import com.vipul.agrishare.repository.BookingRepository;
import com.vipul.agrishare.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final int MAX_LIST = 50;

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final RatingService ratingService;

    /** Either side of a COMPLETED booking may leave one review of the other. */
    @Transactional
    public ReviewResponse create(Long userId, Long bookingId, ReviewRequest request) {
        Booking booking = bookingRepository.findDetailedById(bookingId)
                .orElseThrow(() -> ApiException.notFound("error.booking.notFound"));

        boolean renter = booking.isRenter(userId);
        if (!renter && !booking.isOwner(userId)) {
            throw ApiException.forbidden("error.forbidden");
        }
        if (booking.getStatus() != Booking.Status.COMPLETED) {
            throw ApiException.conflict("error.review.notCompleted");
        }
        if (reviewRepository.existsByBookingIdAndReviewerId(bookingId, userId)) {
            throw ApiException.conflict("error.review.duplicate");
        }

        User owner = booking.getEquipment().getOwner();
        Review review = Review.builder()
                .booking(booking)
                .reviewer(renter ? booking.getRenter() : owner)
                .reviewee(renter ? owner : booking.getRenter())
                .equipment(booking.getEquipment())
                .reviewerRole(renter ? Review.ReviewerRole.RENTER : Review.ReviewerRole.OWNER)
                .rating(request.rating().shortValue())
                .comment(StringUtils.hasText(request.comment()) ? request.comment().trim() : null)
                .build();
        try {
            reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException e) {
            // double-tap on "Submit": the unique (booking, reviewer) constraint catches the race
            throw ApiException.conflict("error.review.duplicate");
        }
        return toResponse(review);
    }

    @Transactional(readOnly = true)
    public ReviewResponse.Page forEquipment(Long equipmentId) {
        RatingService.RatingSummary rating = ratingService.forOneEquipment(equipmentId);
        List<ReviewResponse> reviews = reviewRepository
                .findByEquipmentIdAndReviewerRoleOrderByCreatedAtDesc(equipmentId, Review.ReviewerRole.RENTER,
                        PageRequest.of(0, MAX_LIST))
                .stream().map(ReviewService::toResponse).toList();
        return new ReviewResponse.Page(rating.average(), rating.count(), reviews);
    }

    @Transactional(readOnly = true)
    public ReviewResponse.Page forUser(Long userId) {
        RatingService.RatingSummary rating = ratingService.forUser(userId);
        List<ReviewResponse> reviews = reviewRepository
                .findByRevieweeIdOrderByCreatedAtDesc(userId, PageRequest.of(0, MAX_LIST))
                .stream().map(ReviewService::toResponse).toList();
        return new ReviewResponse.Page(rating.average(), rating.count(), reviews);
    }

    private static ReviewResponse toResponse(Review r) {
        return new ReviewResponse(
                r.getId(),
                r.getRating(),
                r.getComment(),
                UserSummary.of(r.getReviewer()),
                r.getReviewerRole().name(),
                r.getEquipment().getId(),
                r.getEquipment().getName(),
                r.getCreatedAt()
        );
    }
}
