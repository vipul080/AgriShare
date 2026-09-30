package com.vipul.agrishare.dto;

import com.vipul.agrishare.entity.Booking;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record BookingRequest(

        @NotNull(message = "{validation.booking.equipment.required}")
        Long equipmentId,

        @NotNull(message = "{validation.booking.startDate.required}")
        LocalDate startDate,

        @NotNull(message = "{validation.booking.endDate.required}")
        LocalDate endDate,

        @NotNull(message = "{validation.booking.paymentMethod.required}")
        Booking.PaymentMethod paymentMethod,

        @Size(max = 500)
        String note
) {}
