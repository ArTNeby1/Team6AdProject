package com.loomytrip.backend.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * {@code nearLatitude}/{@code nearLongitude} are optional and only honored when adding a
 * single location (e.g. "+ Add" on an AI-recommended nearby place): when set, the new stop
 * is inserted right after whichever existing stop in that day is geographically closest to
 * this point, instead of always being appended at the end of the day. A place recommended
 * for being near Chinatown should land next to Chinatown in the itinerary, not after
 * whatever the traveler happened to add last.
 */
public record AddTripScheduleRequest(
        @NotNull @Min(1) Integer day,
        @NotEmpty List<String> locationNames,
        BigDecimal nearLatitude,
        BigDecimal nearLongitude
) {
}
