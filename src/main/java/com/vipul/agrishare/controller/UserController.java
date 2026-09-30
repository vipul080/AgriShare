package com.vipul.agrishare.controller;

import com.vipul.agrishare.dto.AccountDeleteRequest;
import com.vipul.agrishare.dto.FcmTokenRequest;
import com.vipul.agrishare.dto.PasswordChangeRequest;
import com.vipul.agrishare.dto.ProfileResponse;
import com.vipul.agrishare.dto.ProfileUpdateRequest;
import com.vipul.agrishare.dto.ReviewResponse;
import com.vipul.agrishare.security.UserPrincipal;
import com.vipul.agrishare.service.ReviewService;
import com.vipul.agrishare.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final ReviewService reviewService;

    @GetMapping("/me")
    public ProfileResponse me(@AuthenticationPrincipal UserPrincipal user) {
        return userService.getProfile(user.getId());
    }

    @PutMapping("/me")
    public ProfileResponse updateMe(@AuthenticationPrincipal UserPrincipal user,
                                    @Valid @RequestBody ProfileUpdateRequest request) {
        return userService.updateProfile(user.getId(), request);
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal UserPrincipal user,
                                               @Valid @RequestBody PasswordChangeRequest request) {
        userService.changePassword(user.getId(), request);
        return ResponseEntity.noContent().build();
    }

    /** Google Play requires in-app account deletion. Blocked while bookings are still open. */
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(@AuthenticationPrincipal UserPrincipal user,
                                         @Valid @RequestBody AccountDeleteRequest request) {
        userService.deleteAccount(user.getId(), request);
        return ResponseEntity.noContent().build();
    }

    /** Public: a farmer's reputation, as owner and as renter. */
    @GetMapping("/{id}/reviews")
    public ReviewResponse.Page reviews(@PathVariable Long id) {
        return reviewService.forUser(id);
    }

    /** Android registers its FCM token after login and whenever Firebase rotates it. */
    @PutMapping("/me/fcm-token")
    public ResponseEntity<Void> updateFcmToken(@AuthenticationPrincipal UserPrincipal user,
                                               @Valid @RequestBody FcmTokenRequest request) {
        userService.updateFcmToken(user.getId(), request.token());
        return ResponseEntity.noContent().build();
    }

    /** On logout, so the phone stops getting pushes for this account. */
    @DeleteMapping("/me/fcm-token")
    public ResponseEntity<Void> clearFcmToken(@AuthenticationPrincipal UserPrincipal user) {
        userService.updateFcmToken(user.getId(), null);
        return ResponseEntity.noContent().build();
    }
}
