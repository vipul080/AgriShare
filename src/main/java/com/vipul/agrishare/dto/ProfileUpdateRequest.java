package com.vipul.agrishare.dto;

import com.vipul.agrishare.entity.User;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Phone is the login id and isn't editable here (would need OTP re-verification). */
public record ProfileUpdateRequest(

        @NotBlank(message = "{validation.name.required}")
        @Size(max = 120)
        String name,

        @Email(message = "{validation.email.invalid}")
        @Size(max = 160)
        String email,

        User.Role role,

        @DecimalMin("-90") @DecimalMax("90")
        Double latitude,

        @DecimalMin("-180") @DecimalMax("180")
        Double longitude,

        @Size(max = 255)
        String address,

        @Pattern(regexp = "^(en|hi|pa|mr|gu|bn|ta|te|kn)$", message = "{validation.language.invalid}")
        String preferredLanguage
) {}
