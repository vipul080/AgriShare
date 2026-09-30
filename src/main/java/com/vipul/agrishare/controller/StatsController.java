package com.vipul.agrishare.controller;

import com.vipul.agrishare.entity.Booking;
import com.vipul.agrishare.repository.BookingRepository;
import com.vipul.agrishare.repository.EquipmentRepository;
import com.vipul.agrishare.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final UserRepository userRepository;
    private final EquipmentRepository equipmentRepository;
    private final BookingRepository bookingRepository;

    public record PublicStats(long farmers, long machines, long completedRentals) {}

    /** Landing-page counters. */
    @GetMapping("/public")
    @Transactional(readOnly = true)
    public PublicStats publicStats() {
        return new PublicStats(
                userRepository.countByDeletedAtIsNull(),
                equipmentRepository.countByActiveTrue(),
                bookingRepository.countByStatus(Booking.Status.COMPLETED)
        );
    }
}
