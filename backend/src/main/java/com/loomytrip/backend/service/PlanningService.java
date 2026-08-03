package com.loomytrip.backend.service;

import com.loomytrip.backend.client.AiPlanningClient;
import com.loomytrip.backend.client.MapPlacesClient;
import com.loomytrip.backend.dto.request.CreateChatMessageRequest;
import com.loomytrip.backend.dto.request.CreatePlanningSessionRequest;
import com.loomytrip.backend.dto.request.UpdateDraftPlaceRequest;
import com.loomytrip.backend.dto.response.PlanningSessionSummaryResponse;
import com.loomytrip.backend.entity.ChatMessage;
import com.loomytrip.backend.entity.ChatRole;
import com.loomytrip.backend.entity.DraftPlace;
import com.loomytrip.backend.entity.PlanningSession;
import com.loomytrip.backend.entity.PlanningSessionStatus;
import com.loomytrip.backend.entity.User;
import com.loomytrip.backend.exception.ApiException;
import com.loomytrip.backend.mapper.EntityMapper;
import com.loomytrip.backend.repository.ChatMessageRepository;
import com.loomytrip.backend.repository.DraftPlaceRepository;
import com.loomytrip.backend.repository.PlanningSessionRepository;
import com.loomytrip.backend.repository.UserRepository;
import com.loomytrip.backend.util.SecurityUtils;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanningService {

    private final PlanningSessionRepository planningSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DraftPlaceRepository draftPlaceRepository;
    private final UserRepository userRepository;
    private final EntityMapper entityMapper;
    private final AiPlanningClient aiPlanningClient;
    private final MapPlacesClient mapPlacesClient;

    public PlanningService(
            PlanningSessionRepository planningSessionRepository,
            ChatMessageRepository chatMessageRepository,
            DraftPlaceRepository draftPlaceRepository,
            UserRepository userRepository,
            EntityMapper entityMapper,
            AiPlanningClient aiPlanningClient,
            MapPlacesClient mapPlacesClient
    ) {
        this.planningSessionRepository = planningSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.draftPlaceRepository = draftPlaceRepository;
        this.userRepository = userRepository;
        this.entityMapper = entityMapper;
        this.aiPlanningClient = aiPlanningClient;
        this.mapPlacesClient = mapPlacesClient;
    }

    @Transactional(readOnly = true)
    public List<PlanningSessionSummaryResponse> listMySessions() {
        return planningSessionRepository.findByUser_IdOrderByUpdatedAtDesc(currentUser().getId()).stream()
                .map(entityMapper::toPlanningSessionSummary)
                .toList();
    }

    @Transactional
    public PlanningSessionSummaryResponse createSession(CreatePlanningSessionRequest request) {
        PlanningSession session = new PlanningSession();
        session.setUser(currentUser());
        session.setTitle(request.title());
        session.setInitialBrief(request.initialBrief());
        session.setStatus(PlanningSessionStatus.ACTIVE);
        PlanningSession saved = planningSessionRepository.save(session);

        if (request.initialBrief() != null && !request.initialBrief().isBlank()) {
            ChatMessage briefMessage = new ChatMessage();
            briefMessage.setSession(saved);
            briefMessage.setRole(ChatRole.user);
            briefMessage.setContent(request.initialBrief());
            chatMessageRepository.save(briefMessage);
        }

        return entityMapper.toPlanningSessionSummary(saved);
    }

    @Transactional
    public void addMessage(Long sessionId, CreateChatMessageRequest request) {
        PlanningSession session = loadOwnedSession(sessionId);
        ChatMessage message = new ChatMessage();
        message.setSession(session);
        message.setRole(request.role());
        message.setContent(request.content());
        chatMessageRepository.save(message);
    }

    /**
     * Placeholder: call AI agent to refine drafts from conversation.
     */
    public Object refineWithAi(Long sessionId) {
        PlanningSession session = loadOwnedSession(sessionId);
        aiPlanningClient.extractTravelInfo(session.getInitialBrief(), null);
        throw new ApiException(
                HttpStatus.NOT_IMPLEMENTED,
                "NOT_IMPLEMENTED",
                "AI chat refinement will update draft_place/draft_activity in a later iteration"
        );
    }

    /**
     * Placeholder: validate draft places via map provider.
     */
    public Object validateDraftPlaces(Long sessionId) {
        loadOwnedSession(sessionId);
        mapPlacesClient.validatePlace(null, null);
        throw new ApiException(
                HttpStatus.NOT_IMPLEMENTED,
                "NOT_IMPLEMENTED",
                "Draft place validation will update validation_status in a later iteration"
        );
    }

    /**
     * Placeholder: confirm session drafts into a formal trip.
     */
    public Object confirmSession(Long sessionId) {
        loadOwnedSession(sessionId);
        throw new ApiException(
                HttpStatus.NOT_IMPLEMENTED,
                "NOT_IMPLEMENTED",
                "Confirm will create trip and set planning_session.confirmed_trip_id"
        );
    }

    @Transactional
    public void updateDraftPlace(Long placeId, UpdateDraftPlaceRequest request) {
        DraftPlace place = draftPlaceRepository.findById(placeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLACE_NOT_FOUND", "Draft place not found"));
        ensureSessionOwner(place.getSession());

        if (request.name() != null) {
            place.setName(request.name());
        }
        if (request.address() != null) {
            place.setAddress(request.address());
        }
        if (request.latitude() != null) {
            place.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            place.setLongitude(request.longitude());
        }
        if (request.category() != null) {
            place.setCategory(request.category());
        }
        if (request.note() != null) {
            place.setNote(request.note());
        }
        draftPlaceRepository.save(place);
    }

    @Transactional
    public void deleteDraftPlace(Long placeId) {
        DraftPlace place = draftPlaceRepository.findById(placeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLACE_NOT_FOUND", "Draft place not found"));
        ensureSessionOwner(place.getSession());
        draftPlaceRepository.delete(place);
    }

    private PlanningSession loadOwnedSession(Long sessionId) {
        PlanningSession session = planningSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Planning session not found"));
        ensureSessionOwner(session);
        return session;
    }

    private void ensureSessionOwner(PlanningSession session) {
        if (!session.getUser().getId().equals(currentUser().getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Session does not belong to current user");
        }
    }

    private User currentUser() {
        return userRepository.findByEmail(SecurityUtils.currentUserEmail())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "User not found"));
    }
}
