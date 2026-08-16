package com.loomytrip.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomytrip.backend.dto.response.AgentValidationLogResponse;
import com.loomytrip.backend.dto.response.PageResponse;
import com.loomytrip.backend.entity.AgentValidationLog;
import com.loomytrip.backend.entity.PlanningSession;
import com.loomytrip.backend.repository.AgentValidationLogRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentValidationLogService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AgentValidationLogRepository logRepository;
    private final ObjectMapper objectMapper;

    public AgentValidationLogService(AgentValidationLogRepository logRepository, ObjectMapper objectMapper) {
        this.logRepository = logRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * The audit row deliberately commits independently: a failed refine must remain
     * inspectable even when its caller's transaction is rolled back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            PlanningSession session,
            String operation,
            Object requestPayload,
            Object responsePayload,
            String outcome
    ) {
        AgentValidationLog log = new AgentValidationLog();
        log.setUser(session.getUser());
        log.setPlanningSession(session);
        log.setOperation(operation);
        log.setRequestPayload(toJson(requestPayload));
        log.setResponsePayload(toJson(responsePayload));
        log.setOutcome(outcome);
        logRepository.save(log);
    }

    @Transactional(readOnly = true)
    public PageResponse<AgentValidationLogResponse> listForAdmin(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<AgentValidationLog> result = logRepository.findAll(
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id"))
        );
        List<AgentValidationLogResponse> content = result.getContent().stream()
                .map(log -> new AgentValidationLogResponse(
                        log.getId(),
                        log.getUser().getId(),
                        log.getUser().getEmail(),
                        log.getPlanningSession() == null ? null : log.getPlanningSession().getId(),
                        log.getOperation(),
                        log.getRequestPayload(),
                        log.getResponsePayload(),
                        log.getOutcome(),
                        log.getCreatedAt()
                ))
                .toList();
        return PageResponse.from(result, content);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            return "{\"serializationError\":true}";
        }
    }
}
