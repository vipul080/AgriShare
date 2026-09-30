package com.vipul.agrishare.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewRequest(

        @NotNull(message = "{validation.review.rating.required}")
        @Min(value = 1, message = "{validation.review.rating.range}")
        @Max(value = 5, message = "{validation.review.rating.range}")
        Integer rating,

        @Size(max = 1000)
        String comment
) {}
