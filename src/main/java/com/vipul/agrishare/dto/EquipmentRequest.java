package com.vipul.agrishare.dto;

import com.vipul.agrishare.entity.Equipment;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record EquipmentRequest(

        @NotBlank(message = "{validation.equipment.name.required}")
        @Size(max = 120)
        String name,

        @NotNull(message = "{validation.equipment.category.required}")
        Equipment.Category category,

        @Size(max = 2000)
        String description,

        @NotNull(message = "{validation.equipment.price.required}")
        @DecimalMin(value = "1.00", message = "{validation.equipment.price.min}")
        @DecimalMax(value = "100000.00", message = "{validation.equipment.price.max}")
        @Digits(integer = 8, fraction = 2)
        BigDecimal pricePerDay,

        @NotNull(message = "{validation.location.required}")
        @DecimalMin("-90") @DecimalMax("90")
        Double latitude,

        @NotNull(message = "{validation.location.required}")
        @DecimalMin("-180") @DecimalMax("180")
        Double longitude,

        @Size(max = 255)
        String address,

        Boolean available
) {}
