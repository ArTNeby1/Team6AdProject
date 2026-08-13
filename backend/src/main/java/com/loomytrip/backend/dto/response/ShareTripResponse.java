package com.loomytrip.backend.dto.response;

/**
 * {@code shareToken} is null when {@code shared} is false (see TripService#unshareTrip) —
 * the frontend builds the public URL itself (e.g. {@code /shared/{shareToken}}), the
 * backend only owns generating/revoking the token.
 */
public record ShareTripResponse(Long tripId, boolean shared, String shareToken) {
}
