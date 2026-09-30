package com.vipul.agrishare.service;

import com.vipul.agrishare.dto.AccountDeleteRequest;
import com.vipul.agrishare.dto.PasswordChangeRequest;
import com.vipul.agrishare.dto.ProfileResponse;
import com.vipul.agrishare.dto.ProfileUpdateRequest;
import com.vipul.agrishare.entity.Booking;
import com.vipul.agrishare.entity.Equipment;
import com.vipul.agrishare.entity.User;
import com.vipul.agrishare.exception.ApiException;
import com.vipul.agrishare.repository.BookingRepository;
import com.vipul.agrishare.repository.EquipmentRepository;
import com.vipul.agrishare.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final EquipmentRepository equipmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final RatingService ratingService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
        return toProfile(load(userId));
    }

    @Transactional
    public ProfileResponse updateProfile(Long userId, ProfileUpdateRequest request) {
        User user = load(userId);
        String email = StringUtils.hasText(request.email()) ? request.email().trim().toLowerCase() : null;
        if (email != null && !email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw ApiException.conflict("error.email.exists");
        }

        user.setName(request.name().trim());
        user.setEmail(email);
        if (request.role() != null) {
            user.setRole(request.role());
        }
        user.setLatitude(request.latitude());
        user.setLongitude(request.longitude());
        user.setAddress(request.address());
        if (request.preferredLanguage() != null) {
            user.setPreferredLanguage(request.preferredLanguage());
        }
        return toProfile(user);
    }

    @Transactional
    public void changePassword(Long userId, PasswordChangeRequest request) {
        User user = load(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("error.password.incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    @Transactional
    public void updateFcmToken(Long userId, String token) {
        load(userId).setFcmToken(token == null ? null : token.trim());
    }

    /**
     * Wipes personal data but keeps the row, so the other farmer's past bookings and
     * reviews still make sense ("Deleted user"). Listings are taken down; the phone is
     * freed so the farmer can register again later.
     */
    @Transactional
    public void deleteAccount(Long userId, AccountDeleteRequest request) {
        User user = load(userId);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.badRequest("error.password.incorrect");
        }
        if (bookingRepository.existsOpenForUser(userId, Booking.BLOCKING_STATUSES)) {
            throw ApiException.conflict("error.account.openBookings");
        }

        for (Equipment equipment : equipmentRepository.findByOwnerIdAndActiveTrue(userId)) {
            equipment.setActive(false);
            equipment.setAvailable(false);
        }

        user.setName("Deleted user");
        user.setPhone("del-" + userId);  // unique, never a valid 10-digit login
        user.setEmail(null);
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setLatitude(null);
        user.setLongitude(null);
        user.setAddress(null);
        user.setFcmToken(null);
        user.setDeletedAt(clock.instant());
    }

    private User load(Long userId) {
        return userRepository.findById(userId)
                .filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("error.user.notFound"));
    }

    private ProfileResponse toProfile(User user) {
        RatingService.RatingSummary rating = ratingService.forUser(user.getId());
        return new ProfileResponse(
                user.getId(),
                user.getName(),
                user.getPhone(),
                user.getEmail(),
                user.getRole().name(),
                user.getLatitude(),
                user.getLongitude(),
                user.getAddress(),
                user.getPreferredLanguage(),
                rating.average(),
                rating.count(),
                user.getCreatedAt()
        );
    }
}
