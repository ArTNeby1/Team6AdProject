package com.loomytrip.backend.service;

import com.loomytrip.backend.dto.response.AdminAnalyticsResponse;
import com.loomytrip.backend.entity.PlanningSession;
import com.loomytrip.backend.entity.PlanningSessionStatus;
import com.loomytrip.backend.entity.Trip;
import com.loomytrip.backend.entity.TripSchedule;
import com.loomytrip.backend.repository.PlanningSessionRepository;
import com.loomytrip.backend.repository.TripRepository;
import com.loomytrip.backend.repository.TripScheduleRepository;
import com.loomytrip.backend.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAnalyticsService {

    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final PlanningSessionRepository planningSessionRepository;
    private final TripScheduleRepository tripScheduleRepository;

    public AdminAnalyticsService(
            UserRepository userRepository,
            TripRepository tripRepository,
            PlanningSessionRepository planningSessionRepository,
            TripScheduleRepository tripScheduleRepository
    ) {
        this.userRepository = userRepository;
        this.tripRepository = tripRepository;
        this.planningSessionRepository = planningSessionRepository;
        this.tripScheduleRepository = tripScheduleRepository;
    }

    @Transactional(readOnly = true)
    public AdminAnalyticsResponse overview(LocalDate from, LocalDate to, String bucket, int limit) {
        Instant fromInclusive = from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toExclusive = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        List<Trip> trips = tripRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(fromInclusive, toExclusive);
        List<PlanningSession> sessionsCreated = planningSessionRepository
                .findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(fromInclusive, toExclusive);
        List<PlanningSession> sessionsUpdated = planningSessionRepository
                .findByUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(fromInclusive, toExclusive);

        Set<Long> activeUserIds = new HashSet<>();
        trips.forEach(trip -> activeUserIds.add(trip.getUser().getId()));
        sessionsUpdated.forEach(session -> activeUserIds.add(session.getUser().getId()));

        Map<String, long[]> trend = new LinkedHashMap<>();
        Set<Long> tripIds = trips.stream().map(Trip::getId).collect(Collectors.toSet());
        Set<Long> importedTripIds = tripIds.isEmpty()
                ? Set.of()
                : planningSessionRepository.findByConfirmedTrip_IdIn(tripIds).stream()
                        .map(session -> session.getConfirmedTrip().getId())
                        .collect(Collectors.toSet());
        for (Trip trip : trips) {
            String key = bucketKey(trip.getCreatedAt(), bucket);
            long[] values = trend.computeIfAbsent(key, ignored -> new long[3]);
            values[0]++;
            if (importedTripIds.contains(trip.getId())) {
                values[1]++;
            } else {
                values[2]++;
            }
        }

        Map<Long, PopularAccumulator> popularity = new HashMap<>();
        if (!tripIds.isEmpty()) {
            for (TripSchedule schedule : tripScheduleRepository.findAnalyticsSchedulesByTripIds(tripIds)) {
                Long id = schedule.getDestination().getId();
                PopularAccumulator accumulator = popularity.computeIfAbsent(
                        id,
                        ignored -> new PopularAccumulator(id, schedule.getDestination().getName())
                );
                accumulator.scheduleCount++;
                accumulator.tripIds.add(schedule.getTripDay().getTrip().getId());
            }
        }

        // Import completion for F-20 is DRAFT_READY (notification fires then). CONFIRMED is a
        // later conversion step and still counts as a successful import outcome.
        long completed = sessionsUpdated.stream()
                .filter(session -> session.getStatus() == PlanningSessionStatus.DRAFT_READY
                        || session.getStatus() == PlanningSessionStatus.CONFIRMED)
                .count();
        long failed = sessionsUpdated.stream()
                .filter(session -> session.getStatus() == PlanningSessionStatus.FAILED)
                .count();
        long terminal = completed + failed;
        double successRate = terminal == 0 ? 0.0 : (double) completed / terminal;

        return new AdminAnalyticsResponse(
                from,
                to,
                Math.toIntExact(userRepository.count()),
                activeUserIds.size(),
                trend.entrySet().stream()
                        .map(entry -> new AdminAnalyticsResponse.TrendPoint(
                                entry.getKey(), entry.getValue()[0], entry.getValue()[1], entry.getValue()[2]))
                        .toList(),
                popularity.values().stream()
                        .sorted(Comparator.comparingLong((PopularAccumulator value) -> value.scheduleCount).reversed())
                        .limit(Math.max(1, Math.min(limit, 50)))
                        .map(value -> new AdminAnalyticsResponse.PopularDestination(
                                value.destinationId, value.name, value.scheduleCount, value.tripIds.size()))
                        .toList(),
                new AdminAnalyticsResponse.ImportStats(sessionsCreated.size(), completed, failed, successRate),
                Map.of(
                        "activeUsers", "Distinct travelers with a trip creation or planning-session update in the selected range.",
                        "popularDestinations", "Places ranked by trip schedule appearances for trips created in the selected range.",
                        "importSuccessRate", "Successful imports (DRAFT_READY or CONFIRMED) divided by successful plus failed imports updated in the selected range; in-progress imports are excluded."
                )
        );
    }

    private static String bucketKey(Instant timestamp, String bucket) {
        LocalDate date = timestamp.atZone(ZoneOffset.UTC).toLocalDate();
        if ("week".equalsIgnoreCase(bucket)) {
            WeekFields fields = WeekFields.of(Locale.ROOT);
            return date.get(fields.weekBasedYear())
                    + "-W"
                    + String.format("%02d", date.get(fields.weekOfWeekBasedYear()));
        }
        return date.toString();
    }

    private static final class PopularAccumulator {
        private final Long destinationId;
        private final String name;
        private long scheduleCount;
        private final Set<Long> tripIds = new HashSet<>();

        private PopularAccumulator(Long destinationId, String name) {
            this.destinationId = destinationId;
            this.name = name;
        }
    }
}
