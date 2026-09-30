package com.vipul.agrishare.controller;

import com.vipul.agrishare.dto.FcmTokenRequest;
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
