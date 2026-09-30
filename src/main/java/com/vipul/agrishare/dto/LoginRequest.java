package com.vipul.agrishare.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

/**
 * @param identifier email address OR 10-digit phone number
 */
public record LoginRequest(

        @NotBlank(message = "{validation.identifier.required}")
        @JsonAlias({"email", "phone"})
        String identifier,

        @NotBlank(message = "{validation.password.required}")
        String password
) {}
