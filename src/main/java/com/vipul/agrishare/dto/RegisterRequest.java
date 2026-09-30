package com.vipul.agrishare.dto;

import com.vipul.agrishare.entity.User;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "{validation.name.required}")
        @Size(max = 120)
        String name,

        @Email(message = "{validation.email.invalid}")
        @Size(max = 160)
        String email,

        @NotBlank(message = "{validation.phone.required}")
        @Pattern(regexp = "^[6-9][0-9]{9}$", message = "{validation.phone.invalid}")
        String phone,

        @NotBlank(message = "{validation.password.required}")
        @Size(min = 8, max = 72, message = "{validation.password.size}")
        String password,

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
