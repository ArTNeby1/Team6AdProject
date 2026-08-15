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
        Instant fromInstant = from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toExclusive = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        List<Trip> trips = tripRepository.findByCreatedAtBetween(fromInstant, toExclusive);
        List<PlanningSession> sessionsCreated = planningSessionRepository.findByCreatedAtBetween(fromInstant, toExclusive);
        List<PlanningSession> sessionsUpdated = planningSessionRepository.findByUpdatedAtBetween(fromInstant, toExclusive);

        Set<Long> activeUserIds = new HashSet<>();
        trips.forEach(trip -> activeUserIds.add(trip.getUser().getId()));
        sessionsUpdated.forEach(session -> activeUserIds.add(session.getUser().getId()));

        Map<String, long[]> trend = new LinkedHashMap<>();
        Set<Long> importedTripIds = new HashSet<>();
        sessionsCreated.stream()
                .filter(session -> session.getConfirmedTrip() != null)
                .forEach(session -> importedTripIds.add(session.getConfirmedTrip().getId()));
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

        Set<Long> tripIds = trips.stream().map(Trip::getId).collect(java.util.stream.Collectors.toSet());
        Map<Long, PopularAccumulator> popularity = new HashMap<>();
        if (!tripIds.isEmpty()) {
            for (TripSchedule schedule : tripScheduleRepository.findAll()) {
                if (!tripIds.contains(schedule.getTripDay().getTrip().getId())) {
                    continue;
                }
                Long id = schedule.getDestination().getId();
                PopularAccumulator accumulator = popularity.computeIfAbsent(
                        id,
                        ignored -> new PopularAccumulator(id, schedule.getDestination().getName())
                );
                accumulator.scheduleCount++;
                accumulator.tripIds.add(schedule.getTripDay().getTrip().getId());
            }
        }

        long completed = sessionsCreated.stream()
                .filter(session -> session.getStatus() == PlanningSessionStatus.CONFIRMED)
                .count();
        long failed = sessionsCreated.stream()
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
                        "importSuccessRate", "Confirmed imports divided by confirmed plus failed imports; in-progress imports are excluded."
                )
        );
    }

    private static String bucketKey(Instant timestamp, String bucket) {
        LocalDate date = timestamp.atZone(ZoneOffset.UTC).toLocalDate();
        if ("week".equalsIgnoreCase(bucket)) {
            WeekFields fields = WeekFields.of(Locale.ROOT);
            return date.getYear() + "-W" + String.format("%02d", date.get(fields.weekOfWeekBasedYear()));
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
