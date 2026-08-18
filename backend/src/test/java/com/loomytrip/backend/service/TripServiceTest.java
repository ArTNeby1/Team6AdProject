package com.loomytrip.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loomytrip.backend.client.AiPlanItineraryResult;
import com.loomytrip.backend.client.AiPlanningClient;
import com.loomytrip.backend.client.AiRecommendResult;
import com.loomytrip.backend.client.RoutingClient;
import com.loomytrip.backend.dto.request.AddTripScheduleRequest;
import com.loomytrip.backend.dto.request.BulkUpdateSchedulesRequest;
import com.loomytrip.backend.dto.request.CreateTripRequest;
import com.loomytrip.backend.dto.request.UpdateTripRequest;
import com.loomytrip.backend.dto.response.GenerateItineraryResponse;
import com.loomytrip.backend.dto.response.ShareTripResponse;
import com.loomytrip.backend.dto.response.TripRouteResponse;
import com.loomytrip.backend.dto.response.TripSummaryResponse;
import com.loomytrip.backend.entity.Destination;
import com.loomytrip.backend.entity.Trip;
import com.loomytrip.backend.entity.TripDay;
import com.loomytrip.backend.entity.TripPreference;
import com.loomytrip.backend.entity.TripSchedule;
import com.loomytrip.backend.entity.TripTransport;
import com.loomytrip.backend.entity.User;
import com.loomytrip.backend.exception.ApiException;
import com.loomytrip.backend.mapper.EntityMapper;
import com.loomytrip.backend.repository.TripDayRepository;
import com.loomytrip.backend.repository.TripPreferenceRepository;
import com.loomytrip.backend.repository.TripRepository;
import com.loomytrip.backend.repository.TripScheduleRepository;
import com.loomytrip.backend.repository.TripTransportRepository;
import com.loomytrip.backend.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TripServiceTest {

    @Mock private TripRepository tripRepository;
    @Mock private TripDayRepository tripDayRepository;
    @Mock private TripScheduleRepository tripScheduleRepository;
    @Mock private TripTransportRepository tripTransportRepository;
    @Mock private TripPreferenceRepository tripPreferenceRepository;
    @Mock private UserRepository userRepository;
    @Mock private DestinationService destinationService;
    @Mock private RoutingClient routingClient;
    @Mock private AiPlanningClient aiPlanningClient;
    @Mock private EntityMapper entityMapper;

    @InjectMocks
    private TripService tripService;

    private User user;

    @BeforeEach
    void setUp() {
        user = traveler(1L, "traveler@example.com");
        authenticate(user.getEmail());
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createTrip_persistsDaysAndOptionalPreference() {
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> {
            Trip trip = inv.getArgument(0);
            trip.setId(10L);
            return trip;
        });
        stubSummary();

        tripService.createTrip(new CreateTripRequest(
                "SG Weekend", LocalDate.of(2026, 9, 1), 2, "culture", "transit"));

        verify(tripDayRepository).saveAll(anyList());
        ArgumentCaptor<TripPreference> prefCaptor = ArgumentCaptor.forClass(TripPreference.class);
        verify(tripPreferenceRepository).save(prefCaptor.capture());
        assertThat(prefCaptor.getValue().getTravelStyle()).isEqualTo("culture");
        assertThat(prefCaptor.getValue().getPreferTransport()).isEqualTo("transit");
    }

    @Test
    void deleteTrip_deletesOwnedTrip() {
        Trip trip = ownedTrip(10L, 2);
        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));

        tripService.deleteTrip(10L);

        verify(tripRepository).delete(trip);
    }

    @Test
    void updateTrip_expandsDays_andUpdatesPreference() {
        Trip trip = ownedTrip(10L, 2);
        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
        when(tripRepository.save(trip)).thenReturn(trip);
        when(tripPreferenceRepository.findByTrip_Id(10L)).thenReturn(Optional.empty());
        stubSummary();

        tripService.updateTrip(10L, new UpdateTripRequest(
                "Renamed", null, 3, "food", "walking", true));

        assertThat(trip.getTripName()).isEqualTo("Renamed");
        assertThat(trip.getDurationDays()).isEqualTo(3);
        assertThat(trip.isFavorite()).isTrue();
        verify(tripDayRepository).saveAll(anyList());
        verify(tripPreferenceRepository).save(any(TripPreference.class));
    }

    @Test
    void updateTrip_shrinksDays() {
        Trip trip = ownedTrip(10L, 3);
        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
        when(tripRepository.save(trip)).thenReturn(trip);
        when(tripPreferenceRepository.findByTrip_Id(10L)).thenReturn(Optional.empty());
        stubSummary();

        tripService.updateTrip(10L, new UpdateTripRequest(null, null, 1, null, null, null));

        assertThat(trip.getDurationDays()).isEqualTo(1);
        verify(tripDayRepository).deleteByTrip_IdAndDaySequenceGreaterThan(10L, 1);
    }

    @Test
    void shareTrip_isIdempotent() {
        Trip trip = ownedTrip(10L, 1);
        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
        when(tripRepository.save(trip)).thenReturn(trip);

        ShareTripResponse first = tripService.shareTrip(10L);
        String token = first.shareToken();
        ShareTripResponse second = tripService.shareTrip(10L);

        assertThat(first.shared()).isTrue();
        assertThat(second.shareToken()).isEqualTo(token);
        verify(tripRepository).save(trip);
    }

    @Test
    void unshareTrip_clearsToken() {
        Trip trip = ownedTrip(10L, 1);
        trip.setShareToken("abc");
        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
        when(tripRepository.save(trip)).thenReturn(trip);

        ShareTripResponse response = tripService.unshareTrip(10L);

        assertThat(response.shared()).isFalse();
        assertThat(trip.getShareToken()).isNull();
    }

    @Test
    void getSharedTrip_returnsSummary_orNotFound() {
        Trip trip = ownedTrip(10L, 1);
        when(tripRepository.findByShareToken("tok")).thenReturn(Optional.of(trip));
        stubSummary();

        assertThat(tripService.getSharedTrip("tok").id()).isEqualTo(10L);

        when(tripRepository.findByShareToken("gone")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> tripService.getSharedTrip("gone"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("SHARE_NOT_FOUND");
    }

    @Test
    void addSchedules_appendsWhenAiUnavailable() {
        Trip trip = ownedTrip(10L, 2);
        TripDay day = tripDay(20L, trip, 1);
        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
        when(tripDayRepository.findByTrip_IdAndDaySequence(10L, 1)).thenReturn(Optional.of(day));
        when(tripScheduleRepository.findByTripDay_IdOrderBySequenceAsc(20L)).thenReturn(List.of());
        when(aiPlanningClient.recommend(anyList(), anyString(), isNull()))
                .thenReturn(new AiRecommendResult("UNAVAILABLE", null, List.of(), List.of()));
        Destination destination = destination(100L, "Marina Bay Sands", "1.28", "103.85");
        when(destinationService.findOrCreateByName("Marina Bay Sands", null, null, null))
                .thenReturn(destination);
        stubSummary();

        tripService.addSchedules(10L, new AddTripScheduleRequest(
                1, List.of("Marina Bay Sands"), null, null));

        ArgumentCaptor<TripSchedule> scheduleCaptor = ArgumentCaptor.forClass(TripSchedule.class);
        verify(tripScheduleRepository).save(scheduleCaptor.capture());
        assertThat(scheduleCaptor.getValue().getSequence()).isEqualTo(1);
        assertThat(scheduleCaptor.getValue().getDestination()).isSameAs(destination);
        assertThat(scheduleCaptor.getValue().getStartTime()).isNull();
    }

    @Test
    void addSchedules_usesAiOrderAndInsertsNearAnchor() {
        Trip trip = ownedTrip(10L, 1);
        trip.setStartDate(LocalDate.of(2026, 9, 1));
        TripDay day = tripDay(20L, trip, 1);
        Destination chinatown = destination(101L, "Chinatown", "1.283", "103.844");
        Destination gardens = destination(102L, "Gardens by the Bay", "1.281", "103.863");
        TripSchedule existing = schedule(201L, day, chinatown, 1, LocalTime.of(9, 0));

        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
        when(tripDayRepository.findByTrip_IdAndDaySequence(10L, 1)).thenReturn(Optional.of(day));
        when(tripScheduleRepository.findByTripDay_IdOrderBySequenceAsc(20L)).thenReturn(List.of(existing));
        when(aiPlanningClient.recommend(anyList(), eq("2026-09-01"), isNull()))
                .thenReturn(new AiRecommendResult(
                        "OK",
                        "sunny",
                        List.of(new AiRecommendResult.OrderedStop(
                                "Gardens by the Bay", "attraction",
                                gardens.getLatitude(), gardens.getLongitude(),
                                List.of(), 1, "afternoon", true, "nearby")),
                        List.of()
                ));
        when(destinationService.findOrCreateByName(
                eq("Gardens by the Bay"), eq("attraction"), any(), any()))
                .thenReturn(gardens);
        stubSummary();

        tripService.addSchedules(10L, new AddTripScheduleRequest(
                1,
                List.of("Gardens by the Bay"),
                new BigDecimal("1.282"),
                new BigDecimal("103.845")
        ));

        ArgumentCaptor<TripSchedule> scheduleCaptor = ArgumentCaptor.forClass(TripSchedule.class);
        verify(tripScheduleRepository).save(scheduleCaptor.capture());
        assertThat(scheduleCaptor.getValue().getSequence()).isEqualTo(2);
        assertThat(scheduleCaptor.getValue().getStartTime()).isEqualTo(LocalTime.of(13, 0));
    }

    @Test
    void addSchedules_expandsTripWhenDayBeyondDuration() {
        Trip trip = ownedTrip(10L, 1);
        TripDay day = tripDay(21L, trip, 2);
        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
        when(tripRepository.save(trip)).thenReturn(trip);
        when(tripDayRepository.findByTrip_IdAndDaySequence(10L, 2)).thenReturn(Optional.of(day));
        when(tripScheduleRepository.findByTripDay_IdOrderBySequenceAsc(21L)).thenReturn(List.of());
        when(aiPlanningClient.recommend(anyList(), anyString(), isNull()))
                .thenReturn(new AiRecommendResult("UNAVAILABLE", null, List.of(), List.of()));
        when(destinationService.findOrCreateByName("Sentosa", null, null, null))
                .thenReturn(destination(103L, "Sentosa", "1.25", "103.83"));
        stubSummary();

        tripService.addSchedules(10L, new AddTripScheduleRequest(2, List.of("Sentosa"), null, null));

        assertThat(trip.getDurationDays()).isEqualTo(2);
        verify(tripDayRepository).saveAll(anyList());
    }

    @Test
    void bulkUpdateSchedules_movesAcrossDays_andIgnoresBadTime() {
        Trip trip = ownedTrip(10L, 1);
        TripDay day1 = tripDay(20L, trip, 1);
        TripDay day2 = tripDay(21L, trip, 2);
        Destination destination = destination(100L, "MBS", "1.28", "103.85");
        TripSchedule schedule = schedule(201L, day1, destination, 1, LocalTime.of(9, 0));

        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
        when(tripRepository.save(trip)).thenReturn(trip);
        when(tripScheduleRepository.findById(201L)).thenReturn(Optional.of(schedule));
        when(tripDayRepository.findByTrip_IdAndDaySequence(10L, 2)).thenReturn(Optional.of(day2));
        stubSummary();

        tripService.bulkUpdateSchedules(10L, new BulkUpdateSchedulesRequest(List.of(
                new BulkUpdateSchedulesRequest.ScheduleUpdate(201L, 2, 1, "not-a-time", null)
        )));

        assertThat(trip.getDurationDays()).isEqualTo(2);
        assertThat(schedule.getTripDay()).isSameAs(day2);
        assertThat(schedule.getSequence()).isEqualTo(1);
        assertThat(schedule.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        verify(tripScheduleRepository, org.mockito.Mockito.atLeastOnce()).saveAllAndFlush(anyList());
    }

    @Test
    void bulkUpdateSchedules_rejectsForeignSchedule() {
        Trip trip = ownedTrip(10L, 1);
        Trip otherTrip = ownedTrip(99L, 1);
        otherTrip.setUser(traveler(2L, "other@example.com"));
        TripDay otherDay = tripDay(50L, otherTrip, 1);
        TripSchedule foreign = schedule(301L, otherDay, destination(1L, "X", "1.3", "103.8"), 1, null);

        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
        when(tripScheduleRepository.findById(301L)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> tripService.bulkUpdateSchedules(10L, new BulkUpdateSchedulesRequest(List.of(
                new BulkUpdateSchedulesRequest.ScheduleUpdate(301L, 1, 1, null, null)
        ))))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("FORBIDDEN");
    }

    @Test
    void deleteSchedule_removesOwnedStop() {
        Trip trip = ownedTrip(10L, 1);
        TripDay day = tripDay(20L, trip, 1);
        TripSchedule schedule = schedule(201L, day, destination(1L, "X", "1.3", "103.8"), 1, null);
        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
        when(tripScheduleRepository.findById(201L)).thenReturn(Optional.of(schedule));
        stubSummary();

        tripService.deleteSchedule(10L, 201L);

        verify(tripScheduleRepository).delete(schedule);
    }

    @Test
    void deleteSchedule_rejectsMissingAndForeignStops() {
        Trip trip = ownedTrip(10L, 1);
        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
        when(tripScheduleRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.deleteSchedule(10L, 999L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("SCHEDULE_NOT_FOUND");

        Trip foreignTrip = ownedTrip(77L, 1);
        foreignTrip.setUser(traveler(2L, "other@example.com"));
        TripSchedule foreign = schedule(
                301L,
                tripDay(30L, foreignTrip, 1),
                destination(2L, "Foreign", "1.3", "103.8"),
                1,
                null
        );
        when(tripScheduleRepository.findById(301L)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> tripService.deleteSchedule(10L, 301L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("FORBIDDEN");
    }

    @Test
    void deleteDay_rejectsSingleDayTrip() {
        Trip trip = ownedTrip(10L, 1);
        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.deleteDay(10L, 1))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("MIN_DURATION");
    }

    @Test
    void deleteDay_renumbersLaterDays() {
        Trip trip = ownedTrip(10L, 3);
        TripDay day2 = tripDay(22L, trip, 2);
        TripDay day3 = tripDay(23L, trip, 3);
        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
        when(tripDayRepository.findByTrip_IdAndDaySequence(10L, 2)).thenReturn(Optional.of(day2));
        when(tripDayRepository.findByTrip_IdOrderByDaySequenceAsc(10L)).thenReturn(List.of(day3));
        when(tripRepository.save(trip)).thenReturn(trip);
        stubSummary();

        tripService.deleteDay(10L, 2);

        verify(tripDayRepository).delete(day2);
        assertThat(day3.getDaySequence()).isEqualTo(2);
        assertThat(trip.getDurationDays()).isEqualTo(2);
        verify(tripDayRepository).saveAndFlush(day3);
    }

    @Test
    void deleteDay_missingDay_throwsNotFound() {
        Trip trip = ownedTrip(10L, 2);
        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
        when(tripDayRepository.findByTrip_IdAndDaySequence(10L, 9)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.deleteDay(10L, 9))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("TRIP_DAY_NOT_FOUND");
    }

    @Test
    void estimateRoute_persistsLegs_andWarnsOnMissingEstimate() {
        Trip trip = ownedTrip(10L, 1);
        TripDay day = tripDay(20L, trip, 1);
        Destination from = destination(1L, "A", "1.28", "103.85");
        Destination to = destination(2L, "B", "1.29", "103.86");
        TripSchedule s1 = schedule(201L, day, from, 1, null);
        TripSchedule s2 = schedule(202L, day, to, 2, null);

        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
        when(tripDayRepository.findByTrip_IdAndDaySequence(10L, 1)).thenReturn(Optional.of(day));
        when(tripScheduleRepository.findByTripDay_IdOrderBySequenceAsc(20L)).thenReturn(List.of(s1, s2));
        when(tripPreferenceRepository.findByTrip_Id(10L)).thenReturn(Optional.empty());
        when(destinationService.ensureGeocoded(from)).thenReturn(from);
        when(destinationService.ensureGeocoded(to)).thenReturn(to);
        when(routingClient.estimate(any(), any(), any(), any(), any())).thenReturn(Optional.empty());

        TripRouteResponse response = tripService.estimateRoute(10L, 1);

        assertThat(response.legs()).isEmpty();
        assertThat(response.warnings()).anyMatch(w -> w.contains("Could not estimate route leg"));
        verify(tripTransportRepository).deleteByTripDay_Id(20L);
        verify(tripTransportRepository, never()).save(any());
    }

    @Test
    void estimateRoute_savesTransportUsingPreference() {
        Trip trip = ownedTrip(10L, 1);
        TripDay day = tripDay(20L, trip, 1);
        Destination from = destination(1L, "A", "1.28", "103.85");
        Destination to = destination(2L, "B", "1.29", "103.86");
        TripSchedule s1 = schedule(201L, day, from, 1, null);
        TripSchedule s2 = schedule(202L, day, to, 2, null);
        TripPreference preference = new TripPreference();
        preference.setPreferTransport("transit");

        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
        when(tripDayRepository.findByTrip_IdAndDaySequence(10L, 1)).thenReturn(Optional.of(day));
        when(tripScheduleRepository.findByTripDay_IdOrderBySequenceAsc(20L)).thenReturn(List.of(s1, s2));
        when(tripPreferenceRepository.findByTrip_Id(10L)).thenReturn(Optional.of(preference));
        when(destinationService.ensureGeocoded(from)).thenReturn(from);
        when(destinationService.ensureGeocoded(to)).thenReturn(to);
        // estimateRoute() now calls once per TransportMode (F-14, 2026-08-16) — the trip's
        // preferred mode only decides which one counts toward legs()/totals, every mode
        // still gets its own trip_transport row in transports().
        when(routingClient.estimate(any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(new RoutingClient.RouteEstimate(
                        12, new BigDecimal("3.50"), "https://maps.example/a-b", false)));
        when(tripTransportRepository.save(any(TripTransport.class))).thenAnswer(inv -> {
            TripTransport transport = inv.getArgument(0);
            transport.setId(901L);
            return transport;
        });

        TripRouteResponse response = tripService.estimateRoute(10L, 1);

        assertThat(response.legs()).hasSize(1);
        assertThat(response.transports()).hasSize(RoutingClient.TransportMode.values().length);
        assertThat(response.transports())
                .anySatisfy(t -> {
                    assertThat(t.transportType()).isEqualTo("transit");
                    assertThat(t.durationMinutes()).isEqualTo(12);
                });
        assertThat(response.totalDistanceKm()).isEqualByComparingTo("3.50");
        assertThat(response.googleMapsUrl()).contains("origin=");
    }

    @Test
    void estimateRoute_rejectsInvalidDay() {
        Trip trip = ownedTrip(10L, 1);
        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.estimateRoute(10L, 0))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("INVALID_DAY");
    }

    @Test
    void estimateRoute_skipsDestinationThatCannotBeGeocoded() {
        Trip trip = ownedTrip(10L, 1);
        TripDay day = tripDay(20L, trip, 1);
        Destination unresolved = destination(1L, "Unknown", "1.28", "103.85");
        Destination valid = destination(2L, "MBS", "1.29", "103.86");
        TripSchedule first = schedule(201L, day, unresolved, 1, null);
        TripSchedule second = schedule(202L, day, valid, 2, null);

        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
        when(tripDayRepository.findByTrip_IdAndDaySequence(10L, 1)).thenReturn(Optional.of(day));
        when(tripScheduleRepository.findByTripDay_IdOrderBySequenceAsc(20L)).thenReturn(List.of(first, second));
        when(tripPreferenceRepository.findByTrip_Id(10L)).thenReturn(Optional.empty());
        when(destinationService.ensureGeocoded(unresolved)).thenThrow(
                new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "GEOCODE_FAILED", "missing"));
        when(destinationService.ensureGeocoded(valid)).thenReturn(valid);

        TripRouteResponse response = tripService.estimateRoute(10L, 1);

        assertThat(response.legs()).isEmpty();
        assertThat(response.warnings()).anyMatch(w -> w.contains("Could not geocode Unknown"));
        verify(routingClient, never()).estimate(any(), any(), any(), any(), any());
    }

    @Test
    void generateItinerary_writesBackAiPlan() {
        Trip trip = ownedTrip(10L, 1);
        trip.setStartDate(LocalDate.of(2026, 9, 1));
        TripDay day = tripDay(20L, trip, 1);
        Destination destination = destination(1L, "Marina Bay Sands", "1.28", "103.85");
        TripSchedule schedule = schedule(201L, day, destination, 1, null);

        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
        when(tripScheduleRepository.findByTripDay_Trip_IdOrderByTripDay_DaySequenceAscSequenceAsc(10L))
                .thenReturn(List.of(schedule));
        when(destinationService.ensureGeocoded(destination)).thenReturn(destination);
        when(aiPlanningClient.planItinerary(anyList(), eq("2026-09-01"), eq(1)))
                .thenReturn(new AiPlanItineraryResult(
                        "OK",
                        List.of(new AiPlanItineraryResult.PlannedDay(
                                1,
                                "2026-09-01",
                                "sunny",
                                List.of(new AiPlanItineraryResult.PlannedStop(
                                        "Marina Bay Sands", "attraction",
                                        destination.getLatitude(), destination.getLongitude(),
                                        List.of(), 1, "morning", true, "start here"
                                ))
                        ))
                ));
        when(tripDayRepository.findByTrip_IdAndDaySequence(10L, 1)).thenReturn(Optional.of(day));

        GenerateItineraryResponse response = tripService.generateItinerary(10L);

        assertThat(response.status()).isEqualTo("OK");
        assertThat(schedule.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(schedule.getNote()).isEqualTo("start here");
        verify(tripScheduleRepository).saveAllAndFlush(anyList());
        verify(tripScheduleRepository).save(schedule);
    }

    @Test
    void generateItinerary_rejectsEmptyTrip() {
        Trip trip = ownedTrip(10L, 1);
        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
        when(tripScheduleRepository.findByTripDay_Trip_IdOrderByTripDay_DaySequenceAscSequenceAsc(10L))
                .thenReturn(List.of());

        assertThatThrownBy(() -> tripService.generateItinerary(10L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("NO_SCHEDULES");
    }

    @Test
    void loadOwnedTrip_rejectsForeignOwner() {
        Trip trip = ownedTrip(10L, 1);
        trip.setUser(traveler(99L, "other@example.com"));
        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.getTrip(10L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    private void stubSummary() {
        when(tripPreferenceRepository.findByTrip_Id(any())).thenReturn(Optional.empty());
        when(tripScheduleRepository.findByTripDay_Trip_IdOrderByTripDay_DaySequenceAscSequenceAsc(any()))
                .thenReturn(List.of());
        when(entityMapper.toTripSummary(any(), any(), anyList())).thenAnswer(inv -> {
            Trip trip = inv.getArgument(0);
            return new TripSummaryResponse(
                    trip.getId(), trip.getTripName(), trip.getStartDate(), trip.getDurationDays(),
                    null, "NOT_STARTED", null, null, trip.isFavorite(), List.of()
            );
        });
    }

    private static User traveler(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        return user;
    }

    private Trip ownedTrip(Long id, int durationDays) {
        Trip trip = new Trip();
        trip.setId(id);
        trip.setUser(user);
        trip.setTripName("Trip " + id);
        trip.setStartDate(LocalDate.of(2026, 9, 1));
        trip.setDurationDays(durationDays);
        return trip;
    }

    private static TripDay tripDay(Long id, Trip trip, int sequence) {
        TripDay day = new TripDay();
        day.setId(id);
        day.setTrip(trip);
        day.setDaySequence(sequence);
        return day;
    }

    private static Destination destination(Long id, String name, String lat, String lng) {
        Destination destination = new Destination();
        destination.setId(id);
        destination.setName(name);
        destination.setLatitude(new BigDecimal(lat));
        destination.setLongitude(new BigDecimal(lng));
        destination.setCategory("attraction");
        return destination;
    }

    private static TripSchedule schedule(
            Long id, TripDay day, Destination destination, int sequence, LocalTime startTime
    ) {
        TripSchedule schedule = new TripSchedule();
        schedule.setId(id);
        schedule.setTripDay(day);
        schedule.setDestination(destination);
        schedule.setSequence(sequence);
        schedule.setStartTime(startTime);
        return schedule;
    }

    private static void authenticate(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of())
        );
    }
}
