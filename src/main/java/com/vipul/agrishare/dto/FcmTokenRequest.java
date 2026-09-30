package com.vipul.agrishare.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FcmTokenRequest(
        @NotBlank(message = "{validation.fcmToken.required}")
        @Size(max = 512)
        String token
) {}
