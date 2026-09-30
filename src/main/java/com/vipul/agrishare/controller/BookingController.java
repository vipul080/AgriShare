package com.vipul.agrishare.controller;

import com.vipul.agrishare.dto.BookingActionRequest;
import com.vipul.agrishare.dto.BookingRequest;
import com.vipul.agrishare.dto.BookingResponse;
import com.vipul.agrishare.dto.PaymentVerifyRequest;
import com.vipul.agrishare.dto.ReviewRequest;
import com.vipul.agrishare.dto.ReviewResponse;
import com.vipul.agrishare.security.UserPrincipal;
import com.vipul.agrishare.service.BookingService;
import com.vipul.agrishare.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final ReviewService reviewService;

    @PostMapping
    public ResponseEntity<BookingResponse> create(@AuthenticationPrincipal UserPrincipal user,
                                                  @Valid @RequestBody BookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.create(user.getId(), request));
    }

    /** Bookings I made as a renter. */
    @GetMapping("/mine")
    public List<BookingResponse> mine(@AuthenticationPrincipal UserPrincipal user) {
        return bookingService.listMine(user.getId());
    }

    /** Requests for my equipment, as an owner. */
    @GetMapping("/incoming")
    public List<BookingResponse> incoming(@AuthenticationPrincipal UserPrincipal user) {
        return bookingService.listIncoming(user.getId());
    }

    @GetMapping("/{id}")
    public BookingResponse get(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long id) {
        return bookingService.get(user.getId(), id);
    }

    @PostMapping("/{id}/payment/verify")
    public BookingResponse verifyPayment(@AuthenticationPrincipal UserPrincipal user,
                                         @PathVariable Long id,
                                         @Valid @RequestBody PaymentVerifyRequest request) {
        return bookingService.verifyPayment(user.getId(), id, request);
    }

    @PostMapping("/{id}/approve")
    public BookingResponse approve(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long id) {
        return bookingService.approve(user.getId(), id);
    }

    @PostMapping("/{id}/reject")
    public BookingResponse reject(@AuthenticationPrincipal UserPrincipal user,
                                  @PathVariable Long id,
                                  @Valid @RequestBody(required = false) BookingActionRequest request) {
        return bookingService.reject(user.getId(), id, request == null ? null : request.reason());
    }

    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long id) {
        return bookingService.cancel(user.getId(), id);
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<ReviewResponse> review(@AuthenticationPrincipal UserPrincipal user,
                                                 @PathVariable Long id,
                                                 @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.create(user.getId(), id, request));
    }

    @PostMapping("/{id}/complete")
    public BookingResponse complete(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long id) {
        return bookingService.complete(user.getId(), id);
    }
}
