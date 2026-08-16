package com.loomytrip.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loomytrip.backend.client.AiPlanningClient;
import com.loomytrip.backend.client.MapPlacesClient;
import com.loomytrip.backend.dto.response.PlanningSessionDetailResponse;
import com.loomytrip.backend.entity.ChatMessage;
import com.loomytrip.backend.entity.ChatRole;
import com.loomytrip.backend.entity.DraftPlace;
import com.loomytrip.backend.entity.PlanningSession;
import com.loomytrip.backend.entity.PlanningSessionStatus;
import com.loomytrip.backend.entity.User;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class PlanningServiceAuditTest {

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

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void processInitialImport_recordsSuccessAudit() {
        PlanningSession session = processingSession(11L, "Marina Bay Sands tomorrow");
        when(planningSessionRepository.findById(11L)).thenReturn(Optional.of(session));
        Map<String, Object> result = okExtraction("Marina Bay Sands");
        when(aiPlanningClient.extractTravelInfo("Marina Bay Sands tomorrow", null)).thenReturn(result);
        when(mapPlacesClient.validatePlace(eq("Marina Bay Sands"), isNull())).thenReturn(Optional.empty());
        when(draftPlaceRepository.save(any(DraftPlace.class))).thenAnswer(invocation -> {
            DraftPlace place = invocation.getArgument(0);
            place.setId(101L);
            return place;
        });

        planningService.processInitialImport(11L);

        ArgumentCaptor<Map<String, Object>> requestCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, Object>> responseCaptor = ArgumentCaptor.forClass(Map.class);
        verify(agentValidationLogService).record(
                eq(session),
                eq("IMPORT"),
                requestCaptor.capture(),
                responseCaptor.capture(),
                eq("SUCCESS")
        );
        assertThat(requestCaptor.getValue())
                .containsEntry("raw_content", "Marina Bay Sands tomorrow")
                .containsEntry("source_url", null);
        assertThat(responseCaptor.getValue()).isSameAs(result);
        verify(notificationService).createImportNotification(session, true, null);
        assertThat(session.getStatus()).isEqualTo(PlanningSessionStatus.DRAFT_READY);
    }

    @Test
    void processInitialImport_recordsFailedAudit_whenAiThrows() {
        PlanningSession session = processingSession(12L, "bad brief");
        when(planningSessionRepository.findById(12L)).thenReturn(Optional.of(session));
        when(aiPlanningClient.extractTravelInfo("bad brief", null))
                .thenThrow(new RuntimeException("ml down"));

        planningService.processInitialImport(12L);

        ArgumentCaptor<Map<String, Object>> responseCaptor = ArgumentCaptor.forClass(Map.class);
        verify(agentValidationLogService).record(
                eq(session),
                eq("IMPORT"),
                any(),
                responseCaptor.capture(),
                eq("FAILED")
        );
        assertThat(responseCaptor.getValue()).containsEntry("error", "RuntimeException");
        assertThat(session.getStatus()).isEqualTo(PlanningSessionStatus.FAILED);
        verify(notificationService).createImportNotification(eq(session), eq(false), any());
    }

    @Test
    void processInitialImport_recordsFailedAudit_whenNoPlaces() {
        PlanningSession session = processingSession(13L, "Singapore notes");
        when(planningSessionRepository.findById(13L)).thenReturn(Optional.of(session));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "OK");
        result.put("destination", "Singapore");
        result.put("places", List.of());
        when(aiPlanningClient.extractTravelInfo("Singapore notes", null)).thenReturn(result);

        planningService.processInitialImport(13L);

        verify(agentValidationLogService).record(
                eq(session),
                eq("IMPORT"),
                any(),
                eq(result),
                eq("FAILED")
        );
        assertThat(session.getStatus()).isEqualTo(PlanningSessionStatus.FAILED);
        assertThat(session.getFailureCode()).isEqualTo("IMPORT_FAILED");
    }

    @Test
    void refineWithAi_recordsSuccessAudit() {
        User user = traveler(1L, "traveler@example.com");
        authenticate(user.getEmail());
        PlanningSession session = ownedSession(21L, user, PlanningSessionStatus.DRAFT_READY);
        when(planningSessionRepository.findById(21L)).thenReturn(Optional.of(session));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        ChatMessage message = new ChatMessage();
        message.setRole(ChatRole.user);
        message.setContent("Add Gardens by the Bay");
        when(chatMessageRepository.findBySession_IdOrderByCreatedAtAsc(21L)).thenReturn(List.of(message));

        Map<String, Object> result = okExtraction("Gardens by the Bay");
        when(aiPlanningClient.refineFromChat(any(), isNull())).thenReturn(result);
        when(mapPlacesClient.validatePlace(eq("Gardens by the Bay"), isNull())).thenReturn(Optional.empty());
        when(draftPlaceRepository.save(any(DraftPlace.class))).thenAnswer(invocation -> {
            DraftPlace place = invocation.getArgument(0);
            place.setId(201L);
            return place;
        });
        when(draftPlaceRepository.findBySession_Id(21L)).thenReturn(List.of());
        when(draftActivityRepository.findBySession_Id(21L)).thenReturn(List.of());
        when(entityMapper.toPlanningSessionDetail(eq(session), any(), any()))
                .thenReturn(new PlanningSessionDetailResponse(
                        21L, "Trip", "brief", PlanningSessionStatus.DRAFT_READY,
                        null, null, null, null, List.of(), null
                ));

        planningService.refineWithAi(21L);

        ArgumentCaptor<Map<String, Object>> requestCaptor = ArgumentCaptor.forClass(Map.class);
        verify(agentValidationLogService).record(
                eq(session),
                eq("REFINE"),
                requestCaptor.capture(),
                eq(result),
                eq("SUCCESS")
        );
        assertThat(requestCaptor.getValue()).containsEntry("preference_text", null);
        assertThat(requestCaptor.getValue().get("messages")).isEqualTo(
                List.of(Map.of("role", "user", "content", "Add Gardens by the Bay"))
        );
        verify(draftPlaceRepository).deleteBySession_Id(21L);
        verify(draftActivityRepository).deleteBySession_Id(21L);
    }

    @Test
    void refineWithAi_recordsFailedAudit_andRethrows() {
        User user = traveler(2L, "traveler@example.com");
        authenticate(user.getEmail());
        PlanningSession session = ownedSession(22L, user, PlanningSessionStatus.DRAFT_READY);
        when(planningSessionRepository.findById(22L)).thenReturn(Optional.of(session));
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(chatMessageRepository.findBySession_IdOrderByCreatedAtAsc(22L)).thenReturn(List.of());
        when(aiPlanningClient.refineFromChat(any(), isNull()))
                .thenThrow(new ApiException(
                        org.springframework.http.HttpStatus.BAD_GATEWAY,
                        "AI_SERVICE_UNAVAILABLE",
                        "down"
                ));

        assertThatThrownBy(() -> planningService.refineWithAi(22L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("down");

        ArgumentCaptor<Map<String, Object>> responseCaptor = ArgumentCaptor.forClass(Map.class);
        verify(agentValidationLogService).record(
                eq(session),
                eq("REFINE"),
                any(),
                responseCaptor.capture(),
                eq("FAILED")
        );
        assertThat(responseCaptor.getValue()).containsEntry("error", "ApiException");
        verify(draftPlaceRepository, never()).deleteBySession_Id(anyLong());
    }

    private static Map<String, Object> okExtraction(String placeName) {
        Map<String, Object> place = new LinkedHashMap<>();
        place.put("name", placeName);
        place.put("type", "attraction");
        place.put("activities", List.of("Visit"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "OK");
        result.put("destination", "Singapore");
        result.put("places", List.of(place));
        return result;
    }

    private static PlanningSession processingSession(Long id, String brief) {
        PlanningSession session = new PlanningSession();
        session.setId(id);
        session.setInitialBrief(brief);
        session.setStatus(PlanningSessionStatus.PROCESSING);
        session.setUser(traveler(99L, "owner@example.com"));
        return session;
    }

    private static PlanningSession ownedSession(Long id, User user, PlanningSessionStatus status) {
        PlanningSession session = new PlanningSession();
        session.setId(id);
        session.setTitle("Trip");
        session.setInitialBrief("brief");
        session.setStatus(status);
        session.setUser(user);
        return session;
    }

    private static User traveler(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        return user;
    }

    private static void authenticate(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", java.util.List.of())
        );
    }
}
