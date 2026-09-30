package com.vipul.agrishare.dto;

import jakarta.validation.constraints.NotBlank;

/** Password re-entry so a borrowed/unlocked phone can't wipe the account. */
public record AccountDeleteRequest(
        @NotBlank(message = "{validation.password.required}")
        String password
) {}
