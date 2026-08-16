package com.loomytrip.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loomytrip.backend.client.AiPlanningClient;
import com.loomytrip.backend.client.AiRecommendResult;
import com.loomytrip.backend.client.MapPlacesClient;
import com.loomytrip.backend.dto.request.ConfirmSessionRequest;
import com.loomytrip.backend.dto.request.CreatePlanningSessionRequest;
import com.loomytrip.backend.dto.request.UpdateDraftActivityRequest;
import com.loomytrip.backend.dto.request.UpdateDraftPlaceRequest;
import com.loomytrip.backend.dto.response.ConfirmSessionResponse;
import com.loomytrip.backend.dto.response.PlanningSessionDetailResponse;
import com.loomytrip.backend.entity.Destination;
import com.loomytrip.backend.entity.DraftActivity;
import com.loomytrip.backend.entity.DraftPlace;
import com.loomytrip.backend.entity.PlanningSession;
import com.loomytrip.backend.entity.PlanningSessionStatus;
import com.loomytrip.backend.entity.Trip;
import com.loomytrip.backend.entity.TripDay;
import com.loomytrip.backend.entity.User;
import com.loomytrip.backend.entity.ValidationStatus;
import com.loomytrip.backend.event.InitialImportRequestedEvent;
import com.loomytrip.backend.exception.ApiException;
import com.loomytrip.backend.mapper.EntityMapper;
import com.loomytrip.backend.repository.ChatMessageRepository;
import com.loomytrip.backend.repository.DraftActivityRepository;
import com.loomytrip.backend.repository.DraftPlaceRepository;
import com.loomytrip.backend.repository.PlanningSessionRepository;
import com.loomytrip.backend.repository.TripDayRepository;
import com.loomytrip.backend.repository.TripRepository;
import com.loomytrip.backend.repository.TripScheduleRepository;
import com.loomytrip.backend.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanningServiceCoreTest {

    @Mock private PlanningSessionRepository planningSessionRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private DraftPlaceRepository draftPlaceRepository;
    @Mock private DraftActivityRepository draftActivityRepository;
    @Mock private UserRepository userRepository;
    @Mock private DestinationService destinationService;
    @Mock private TripRepository tripRepository;
    @Mock private TripDayRepository tripDayRepository;
    @Mock private TripScheduleRepository tripScheduleRepository;
    @Mock private EntityMapper entityMapper;
    @Mock private AiPlanningClient aiPlanningClient;
    @Mock private MapPlacesClient mapPlacesClient;
    @Mock private NotificationService notificationService;
    @Mock private AgentValidationLogService agentValidationLogService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private TripService tripService;

    @InjectMocks
    private PlanningService planningService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("traveler@example.com");
        authenticate(user.getEmail());
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createSession_withoutBrief_staysActive_andDoesNotPublishImport() {
        when(planningSessionRepository.save(any(PlanningSession.class))).thenAnswer(inv -> {
            PlanningSession session = inv.getArgument(0);
            session.setId(11L);
            return session;
        });
        stubSessionDetail(11L, ownedSession(11L, PlanningSessionStatus.ACTIVE));

        PlanningSessionDetailResponse response = planningService.createSession(
                new CreatePlanningSessionRequest("Manual", "  "));

        assertThat(response.status()).isEqualTo(PlanningSessionStatus.ACTIVE);
        verify(eventPublisher, org.mockito.Mockito.never()).publishEvent(any());
        verify(chatMessageRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void createSession_withBrief_marksProcessing_andPublishesEvent() {
        when(planningSessionRepository.save(any(PlanningSession.class))).thenAnswer(inv -> {
            PlanningSession session = inv.getArgument(0);
            session.setId(12L);
            return session;
        });
        stubSessionDetail(12L, ownedSession(12L, PlanningSessionStatus.PROCESSING));

        planningService.createSession(new CreatePlanningSessionRequest("Import", "Visit MBS tomorrow"));

        ArgumentCaptor<InitialImportRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(InitialImportRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().sessionId()).isEqualTo(12L);
        verify(chatMessageRepository).save(any());
    }

    @Test
    void validateDraftPlaces_marksCoordPlacesValid_withoutGeocode() {
        PlanningSession session = ownedSession(20L, PlanningSessionStatus.DRAFT_READY);
        DraftPlace place = draftPlace(201L, session, "MBS", new BigDecimal("1.28"), new BigDecimal("103.85"));
        stubSessionDetail(20L, session);
        when(draftPlaceRepository.findBySession_Id(20L)).thenReturn(List.of(place));
        when(destinationService.findOrCreateByName(anyString(), any(), any(), any()))
                .thenReturn(destination(9L, "MBS"));

        planningService.validateDraftPlaces(20L);

        assertThat(place.getValidationStatus()).isEqualTo(ValidationStatus.VALID);
        verify(mapPlacesClient, org.mockito.Mockito.never()).validatePlace(any(), any());
        verify(draftPlaceRepository).save(place);
    }

    @Test
    void validateDraftPlaces_marksInvalid_whenGeocodeMisses() {
        PlanningSession session = ownedSession(21L, PlanningSessionStatus.DRAFT_READY);
        DraftPlace place = draftPlace(202L, session, "Nowhere", null, null);
        stubSessionDetail(21L, session);
        when(draftPlaceRepository.findBySession_Id(21L)).thenReturn(List.of(place));
        when(mapPlacesClient.validatePlace("Nowhere", null)).thenReturn(Optional.empty());

        planningService.validateDraftPlaces(21L);

        assertThat(place.getValidationStatus()).isEqualTo(ValidationStatus.INVALID);
    }

    @Test
    void confirmSession_rejectsProcessingFailedMissingDaysAndUnvalidated() {
        PlanningSession processing = ownedSession(30L, PlanningSessionStatus.PROCESSING);
        when(planningSessionRepository.findById(30L)).thenReturn(Optional.of(processing));
        assertThatThrownBy(() -> planningService.confirmSession(30L, new ConfirmSessionRequest(2)))
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("IMPORT_IN_PROGRESS");

        PlanningSession failed = ownedSession(31L, PlanningSessionStatus.FAILED);
        when(planningSessionRepository.findById(31L)).thenReturn(Optional.of(failed));
        assertThatThrownBy(() -> planningService.confirmSession(31L, new ConfirmSessionRequest(2)))
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("IMPORT_FAILED");

        PlanningSession ready = ownedSession(32L, PlanningSessionStatus.DRAFT_READY);
        when(planningSessionRepository.findById(32L)).thenReturn(Optional.of(ready));
        when(draftPlaceRepository.findBySession_Id(32L)).thenReturn(List.of());
        assertThatThrownBy(() -> planningService.confirmSession(32L, new ConfirmSessionRequest(2)))
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("NO_PLACES");

        DraftPlace place = draftPlace(203L, ready, "MBS", new BigDecimal("1.28"), new BigDecimal("103.85"));
        place.setValidationStatus(ValidationStatus.VALID);
        when(draftPlaceRepository.findBySession_Id(32L)).thenReturn(List.of(place));
        when(draftActivityRepository.findBySession_Id(32L)).thenReturn(List.of());
        assertThatThrownBy(() -> planningService.confirmSession(32L, null))
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("DAYS_REQUIRED");

        place.setValidationStatus(ValidationStatus.UNVALIDATED);
        assertThatThrownBy(() -> planningService.confirmSession(32L, new ConfirmSessionRequest(1)))
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("PLACES_NOT_VALIDATED");
    }

    @Test
    void confirmSession_rejectsConfirmedAndOutOfRangeDuration() {
        PlanningSession confirmed = ownedSession(33L, PlanningSessionStatus.CONFIRMED);
        when(planningSessionRepository.findById(33L)).thenReturn(Optional.of(confirmed));

        assertThatThrownBy(() -> planningService.confirmSession(33L, new ConfirmSessionRequest(1)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("SESSION_ALREADY_CONFIRMED");

        PlanningSession ready = ownedSession(34L, PlanningSessionStatus.DRAFT_READY);
        DraftPlace validPlace = draftPlace(
                204L,
                ready,
                "MBS",
                new BigDecimal("1.28"),
                new BigDecimal("103.85")
        );
        validPlace.setValidationStatus(ValidationStatus.VALID);
        when(planningSessionRepository.findById(34L)).thenReturn(Optional.of(ready));
        when(draftPlaceRepository.findBySession_Id(34L)).thenReturn(List.of(validPlace));

        assertThatThrownBy(() -> planningService.confirmSession(34L, new ConfirmSessionRequest(31)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("INVALID_DURATION");
    }

    @Test
    void confirmSession_createsTripAndMarksConfirmed() {
        PlanningSession session = ownedSession(40L, PlanningSessionStatus.DRAFT_READY);
        session.setTitle("SG Trip");
        DraftPlace place = draftPlace(204L, session, "MBS", new BigDecimal("1.28"), new BigDecimal("103.85"));
        place.setValidationStatus(ValidationStatus.VALID);
        when(planningSessionRepository.findById(40L)).thenReturn(Optional.of(session));
        when(draftPlaceRepository.findBySession_Id(40L)).thenReturn(List.of(place));
        when(draftActivityRepository.findBySession_Id(40L)).thenReturn(List.of());
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> {
            Trip trip = inv.getArgument(0);
            trip.setId(400L);
            return trip;
        });
        when(tripDayRepository.save(any(TripDay.class))).thenAnswer(inv -> {
            TripDay day = inv.getArgument(0);
            day.setId(401L);
            return day;
        });
        when(destinationService.findOrCreateByName(eq("MBS"), any(), any(), any()))
                .thenReturn(destination(9L, "MBS"));
        when(aiPlanningClient.recommend(anyList(), anyString(), isNull()))
                .thenReturn(new AiRecommendResult("OK", "sunny", List.of(), List.of()));
        when(tripScheduleRepository.findVisitedDestinationNamesByUserId(1L)).thenReturn(Set.of());
        when(tripService.generateItinerary(400L)).thenThrow(new ApiException(
                org.springframework.http.HttpStatus.BAD_GATEWAY, "AI_SERVICE_UNAVAILABLE", "down"));

        ConfirmSessionResponse response = planningService.confirmSession(40L, new ConfirmSessionRequest(1));

        assertThat(response.id()).isEqualTo(400L);
        assertThat(response.weatherSummary()).isEqualTo("sunny");
        assertThat(session.getStatus()).isEqualTo(PlanningSessionStatus.CONFIRMED);
        assertThat(session.getConfirmedTrip().getId()).isEqualTo(400L);
        verify(tripScheduleRepository).save(any());
    }

    @Test
    void updateAndDeleteDraftPlace_enforceOwnershipAndEditableState() {
        PlanningSession session = ownedSession(50L, PlanningSessionStatus.DRAFT_READY);
        DraftPlace place = draftPlace(205L, session, "Old", null, null);
        when(draftPlaceRepository.findById(205L)).thenReturn(Optional.of(place));

        planningService.updateDraftPlace(205L, new UpdateDraftPlaceRequest(
                "New", "addr", null, null, "food", "note", 2, LocalTime.of(10, 0)));

        assertThat(place.getName()).isEqualTo("New");
        assertThat(place.getSuggestedDay()).isEqualTo(2);
        assertThat(place.getStartTime()).isEqualTo(LocalTime.of(10, 0));

        planningService.deleteDraftPlace(205L);
        verify(draftPlaceRepository).delete(place);
    }

    @Test
    void updateDraftActivity_updatesDayAndTime() {
        PlanningSession session = ownedSession(51L, PlanningSessionStatus.DRAFT_READY);
        DraftPlace place = draftPlace(206L, session, "MBS", null, null);
        DraftActivity activity = new DraftActivity();
        activity.setId(300L);
        activity.setSession(session);
        activity.setDraftPlace(place);
        activity.setTitle("Visit");
        when(draftActivityRepository.findById(300L)).thenReturn(Optional.of(activity));

        planningService.updateDraftActivity(300L, new UpdateDraftActivityRequest(2, LocalTime.of(14, 0)));

        assertThat(activity.getSuggestedDay()).isEqualTo(2);
        assertThat(activity.getStartTime()).isEqualTo(LocalTime.of(14, 0));
    }

    @Test
    void draftMutations_rejectMissingResourcesAndConfirmedSession() {
        when(draftPlaceRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> planningService.updateDraftPlace(
                999L,
                new UpdateDraftPlaceRequest(null, null, null, null, null, null, null, null)
        ))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("PLACE_NOT_FOUND");

        when(draftActivityRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> planningService.updateDraftActivity(
                999L,
                new UpdateDraftActivityRequest(null, null)
        ))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("ACTIVITY_NOT_FOUND");

        PlanningSession confirmed = ownedSession(52L, PlanningSessionStatus.CONFIRMED);
        DraftPlace place = draftPlace(207L, confirmed, "MBS", null, null);
        when(draftPlaceRepository.findById(207L)).thenReturn(Optional.of(place));
        assertThatThrownBy(() -> planningService.deleteDraftPlace(207L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("SESSION_ALREADY_CONFIRMED");
    }

    private void stubSessionDetail(Long sessionId, PlanningSession session) {
        when(planningSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(draftPlaceRepository.findBySession_Id(sessionId)).thenReturn(List.of());
        when(draftActivityRepository.findBySession_Id(sessionId)).thenReturn(List.of());
        when(entityMapper.toPlanningSessionDetail(any(), any(), any()))
                .thenAnswer(inv -> {
                    PlanningSession s = inv.getArgument(0);
                    return new PlanningSessionDetailResponse(
                            s.getId(), s.getTitle(), s.getInitialBrief(), s.getStatus(),
                            null, s.getDurationDays(), s.getFailureCode(), s.getFailureReason(),
                            List.of(), null
                    );
                });
    }

    private PlanningSession ownedSession(Long id, PlanningSessionStatus status) {
        PlanningSession session = new PlanningSession();
        session.setId(id);
        session.setUser(user);
        session.setTitle("Session " + id);
        session.setStatus(status);
        return session;
    }

    private static DraftPlace draftPlace(
            Long id, PlanningSession session, String name, BigDecimal lat, BigDecimal lng
    ) {
        DraftPlace place = new DraftPlace();
        place.setId(id);
        place.setSession(session);
        place.setName(name);
        place.setLatitude(lat);
        place.setLongitude(lng);
        place.setValidationStatus(ValidationStatus.UNVALIDATED);
        return place;
    }

    private static Destination destination(Long id, String name) {
        Destination destination = new Destination();
        destination.setId(id);
        destination.setName(name);
        destination.setLatitude(new BigDecimal("1.28"));
        destination.setLongitude(new BigDecimal("103.85"));
        return destination;
    }

    private static void authenticate(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of())
        );
    }
}
