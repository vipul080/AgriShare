package com.vipul.agrishare.dto;

public record AuthResponse(
        String token,
        Long userId,
        String name,
        String email,
        String phone,
        String role,
        String preferredLanguage
) {}
