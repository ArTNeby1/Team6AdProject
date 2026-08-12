package com.loomytrip.backend.controller;

import com.loomytrip.backend.dto.request.CreateChatMessageRequest;
import com.loomytrip.backend.dto.request.CreatePlanningSessionRequest;
import com.loomytrip.backend.dto.request.UpdateDraftPlaceRequest;
import com.loomytrip.backend.dto.response.ConfirmSessionResponse;
import com.loomytrip.backend.dto.response.PlanningSessionDetailResponse;
import com.loomytrip.backend.dto.response.PlanningSessionSummaryResponse;
import com.loomytrip.backend.service.PlanningService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/planning-sessions")
public class PlanningController {

    private final PlanningService planningService;

    public PlanningController(PlanningService planningService) {
        this.planningService = planningService;
    }

    @GetMapping
    public List<PlanningSessionSummaryResponse> listSessions() {
        return planningService.listMySessions();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlanningSessionDetailResponse createSession(@RequestBody CreatePlanningSessionRequest request) {
        return planningService.createSession(request);
    }

    @GetMapping("/{sessionId}")
    public PlanningSessionDetailResponse getSession(@PathVariable Long sessionId) {
        return planningService.getSession(sessionId);
    }

    @PostMapping("/{sessionId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public void addMessage(
            @PathVariable Long sessionId,
            @Valid @RequestBody CreateChatMessageRequest request
    ) {
        planningService.addMessage(sessionId, request);
    }

    @PostMapping("/{sessionId}/refine")
    public PlanningSessionDetailResponse refine(@PathVariable Long sessionId) {
        return planningService.refineWithAi(sessionId);
    }

    @PostMapping("/{sessionId}/validate-places")
    public PlanningSessionDetailResponse validatePlaces(@PathVariable Long sessionId) {
        return planningService.validateDraftPlaces(sessionId);
    }

    @PostMapping("/{sessionId}/confirm")
    public ConfirmSessionResponse confirm(@PathVariable Long sessionId) {
        return planningService.confirmSession(sessionId);
    }

    @PutMapping("/draft-places/{placeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateDraftPlace(@PathVariable Long placeId, @RequestBody UpdateDraftPlaceRequest request) {
        planningService.updateDraftPlace(placeId, request);
    }

    @DeleteMapping("/draft-places/{placeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDraftPlace(@PathVariable Long placeId) {
        planningService.deleteDraftPlace(placeId);
    }
}
