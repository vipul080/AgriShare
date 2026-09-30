package com.vipul.agrishare.dto;

import java.time.LocalDate;

/** A taken date range (inclusive) — no renter details, safe to show publicly. */
public record BookedRange(LocalDate startDate, LocalDate endDate) {}
