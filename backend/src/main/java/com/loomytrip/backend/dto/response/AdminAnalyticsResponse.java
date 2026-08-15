package com.loomytrip.backend.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record AdminAnalyticsResponse(
        LocalDate from,
        LocalDate to,
        int totalUsers,
        int activeUsers,
        List<TrendPoint> tripCreationTrend,
        List<PopularDestination> popularDestinations,
        ImportStats importStats,
        Map<String, String> definitions
) {
    public record TrendPoint(String bucket, long tripsCreated, long importedTrips, long manualTrips) {
    }

    public record PopularDestination(Long destinationId, String name, long scheduleCount, long tripCount) {
    }

    public record ImportStats(long sessionsStarted, long completed, long failed, double successRate) {
    }
}
