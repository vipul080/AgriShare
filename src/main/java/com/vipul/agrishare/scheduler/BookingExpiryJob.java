package com.vipul.agrishare.scheduler;

import com.vipul.agrishare.entity.Booking;
import com.vipul.agrishare.entity.Booking.Status;
import com.vipul.agrishare.payment.PaymentProperties;
import com.vipul.agrishare.repository.BookingRepository;
import com.vipul.agrishare.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Frees up dates held by bookings nobody is acting on:
 * - AWAITING_PAYMENT: renter abandoned checkout (after checkout-timeout-minutes)
 * - REQUESTED: owner never answered (after authorization-expiry-hours, kept below
 *   Razorpay's 5-day auto-refund window so we expire before the money silently returns)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingExpiryJob {

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final PaymentProperties paymentProperties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    public void expireStaleBookings() {
        Instant now = clock.instant();
        Instant checkoutCutoff = now.minus(Duration.ofMinutes(paymentProperties.checkoutTimeoutMinutes()));
        Instant requestCutoff = now.minus(Duration.ofHours(paymentProperties.authorizationExpiryHours()));

        int expired = expireAll(bookingRepository.findByStatusAndCreatedAtBefore(Status.AWAITING_PAYMENT, checkoutCutoff),
                Status.AWAITING_PAYMENT)
                + expireAll(bookingRepository.findByStatusAndRequestedAtBefore(Status.REQUESTED, requestCutoff),
                Status.REQUESTED);
        if (expired > 0) {
            log.info("Expired {} stale booking(s)", expired);
        }
    }

    private int expireAll(List<Booking> candidates, Status expected) {
        int count = 0;
        for (Booking booking : candidates) {
            try {
                if (bookingService.expireIfStill(booking.getId(), expected)) {
                    count++;
                }
            } catch (ObjectOptimisticLockingFailureException e) {
                // someone approved/paid/cancelled at the same moment — their action wins
                log.debug("Booking {} changed while expiring; skipped", booking.getId());
            }
        }
        return count;
    }
}
