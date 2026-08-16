package com.loomytrip.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loomytrip.backend.dto.response.AdminAnalyticsResponse;
import com.loomytrip.backend.dto.response.CrowdHintResponse;
import com.loomytrip.backend.entity.Destination;
import com.loomytrip.backend.entity.PlanningSession;
import com.loomytrip.backend.entity.PlanningSessionStatus;
import com.loomytrip.backend.entity.Trip;
import com.loomytrip.backend.entity.TripDay;
import com.loomytrip.backend.entity.TripSchedule;
import com.loomytrip.backend.entity.User;
import com.loomytrip.backend.repository.PlanningSessionRepository;
import com.loomytrip.backend.repository.TripRepository;
import com.loomytrip.backend.repository.TripScheduleRepository;
import com.loomytrip.backend.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminAnalyticsAndCrowdHintServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private TripRepository tripRepository;
    @Mock private PlanningSessionRepository planningSessionRepository;
    @Mock private TripScheduleRepository tripScheduleRepository;

    @InjectMocks
    private AdminAnalyticsService analyticsService;

    @Test
    void overview_aggregatesDailyTrendPopularityAndImportOutcomes() {
        User firstUser = user(1L);
        User secondUser = user(2L);
        Trip importedTrip = trip(10L, firstUser, "2026-08-02T10:00:00Z");
        Trip manualTrip = trip(11L, secondUser, "2026-08-02T12:00:00Z");
        PlanningSession importedSession = session(
                100L, firstUser, PlanningSessionStatus.CONFIRMED, "2026-08-01T12:00:00Z");
        importedSession.setConfirmedTrip(importedTrip);
        PlanningSession readySession = session(
                101L, firstUser, PlanningSessionStatus.DRAFT_READY, "2026-08-02T09:00:00Z");
        PlanningSession failedSession = session(
                102L, secondUser, PlanningSessionStatus.FAILED, "2026-08-02T10:00:00Z");

        when(tripRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any()))
                .thenReturn(List.of(importedTrip, manualTrip));
        when(planningSessionRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any()))
                .thenReturn(List.of(importedSession, readySession));
        when(planningSessionRepository.findByUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(any(), any()))
                .thenReturn(List.of(readySession, failedSession));
        when(planningSessionRepository.findByConfirmedTrip_IdIn(Set.of(10L, 11L)))
                .thenReturn(List.of(importedSession));
        when(tripScheduleRepository.findAnalyticsSchedulesByTripIds(Set.of(10L, 11L)))
                .thenReturn(List.of(
                        schedule(importedTrip, 1L, "Marina Bay Sands"),
                        schedule(importedTrip, 1L, "Marina Bay Sands"),
                        schedule(manualTrip, 2L, "Gardens by the Bay")
                ));
        when(userRepository.count()).thenReturn(8L);

        AdminAnalyticsResponse response = analyticsService.overview(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 3),
                "day",
                1
        );

        assertThat(response.totalUsers()).isEqualTo(8);
        assertThat(response.activeUsers()).isEqualTo(2);
        assertThat(response.tripCreationTrend()).containsExactly(
                new AdminAnalyticsResponse.TrendPoint("2026-08-02", 2, 1, 1)
        );
        assertThat(response.popularDestinations()).containsExactly(
                new AdminAnalyticsResponse.PopularDestination(1L, "Marina Bay Sands", 2, 1)
        );
        assertThat(response.importStats().sessionsStarted()).isEqualTo(2);
        assertThat(response.importStats().completed()).isEqualTo(1);
        assertThat(response.importStats().failed()).isEqualTo(1);
        assertThat(response.importStats().successRate()).isEqualTo(0.5);
        verify(tripScheduleRepository).findAnalyticsSchedulesByTripIds(Set.of(10L, 11L));
    }

    @Test
    void overview_groupsWeekAndHandlesAnEmptyRange() {
        Trip trip = trip(10L, user(1L), "2026-01-01T10:00:00Z");
        when(tripRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any()))
                .thenReturn(List.of(trip));
        when(planningSessionRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(any(), any()))
                .thenReturn(List.of());
        when(planningSessionRepository.findByUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(any(), any()))
                .thenReturn(List.of());
        when(planningSessionRepository.findByConfirmedTrip_IdIn(anySet())).thenReturn(List.of());
        when(tripScheduleRepository.findAnalyticsSchedulesByTripIds(anySet())).thenReturn(List.of());
        when(userRepository.count()).thenReturn(0L);

        AdminAnalyticsResponse response = analyticsService.overview(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 1),
                "week",
                999
        );

        assertThat(response.tripCreationTrend()).singleElement()
                .extracting(AdminAnalyticsResponse.TrendPoint::bucket)
                .isEqualTo("2026-W01");
        assertThat(response.popularDestinations()).isEmpty();
        assertThat(response.importStats().successRate()).isZero();
    }

    @Test
    void crowdHint_classifiesEachSeasonalQuarter() {
        CrowdHintService service = new CrowdHintService();

        CrowdHintResponse firstQuarter = service.getHint(LocalDate.of(2026, 1, 15));
        CrowdHintResponse thirdQuarter = service.getHint(LocalDate.of(2026, 8, 15));
        CrowdHintResponse fourthQuarter = service.getHint(LocalDate.of(2026, 11, 15));

        assertThat(firstQuarter).extracting(CrowdHintResponse::quarter, CrowdHintResponse::level)
                .containsExactly(1, "low");
        assertThat(thirdQuarter).extracting(CrowdHintResponse::quarter, CrowdHintResponse::level)
                .containsExactly(3, "medium");
        assertThat(fourthQuarter).extracting(CrowdHintResponse::quarter, CrowdHintResponse::level)
                .containsExactly(4, "high");
        assertThat(fourthQuarter.note()).contains("busier-than-average", "national quarterly proxy");
    }

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private static Trip trip(Long id, User user, String createdAt) {
        Trip trip = new Trip();
        trip.setId(id);
        trip.setUser(user);
        trip.setCreatedAt(Instant.parse(createdAt));
        return trip;
    }

    private static PlanningSession session(
            Long id, User user, PlanningSessionStatus status, String timestamp
    ) {
        PlanningSession session = new PlanningSession();
        session.setId(id);
        session.setUser(user);
        session.setStatus(status);
        session.setCreatedAt(Instant.parse(timestamp));
        session.setUpdatedAt(Instant.parse(timestamp));
        return session;
    }

    private static TripSchedule schedule(Trip trip, Long destinationId, String destinationName) {
        Destination destination = new Destination();
        destination.setId(destinationId);
        destination.setName(destinationName);
        TripDay day = new TripDay();
        day.setTrip(trip);
        TripSchedule schedule = new TripSchedule();
        schedule.setDestination(destination);
        schedule.setTripDay(day);
        return schedule;
    }
}
