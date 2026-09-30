package com.vipul.agrishare.dto;

import jakarta.validation.constraints.Size;

/** Optional free-text reason, e.g. why the owner rejected. */
public record BookingActionRequest(@Size(max = 500) String reason) {}
