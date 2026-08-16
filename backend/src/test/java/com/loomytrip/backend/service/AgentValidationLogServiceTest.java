package com.loomytrip.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomytrip.backend.dto.response.PageResponse;
import com.loomytrip.backend.entity.AgentValidationLog;
import com.loomytrip.backend.entity.PlanningSession;
import com.loomytrip.backend.entity.User;
import com.loomytrip.backend.repository.AgentValidationLogRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AgentValidationLogServiceTest {

    @Mock
    private AgentValidationLogRepository logRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AgentValidationLogService service;

    @Test
    void record_serializesBothPayloads_andLinksSessionToUser() throws Exception {
        PlanningSession session = session(7L, "traveler@example.com");
        when(objectMapper.writeValueAsString(Map.of("raw_content", "Singapore trip")))
                .thenReturn("{\"raw_content\":\"Singapore trip\"}");
        when(objectMapper.writeValueAsString(Map.of("status", "OK")))
                .thenReturn("{\"status\":\"OK\"}");

        service.record(
                session,
                "IMPORT",
                Map.of("raw_content", "Singapore trip"),
                Map.of("status", "OK"),
                "SUCCESS"
        );

        ArgumentCaptor<AgentValidationLog> captor = ArgumentCaptor.forClass(AgentValidationLog.class);
        verify(logRepository).save(captor.capture());
        AgentValidationLog saved = captor.getValue();
        assertThat(saved.getUser()).isSameAs(session.getUser());
        assertThat(saved.getPlanningSession()).isSameAs(session);
        assertThat(saved.getOperation()).isEqualTo("IMPORT");
        assertThat(saved.getRequestPayload()).isEqualTo("{\"raw_content\":\"Singapore trip\"}");
        assertThat(saved.getResponsePayload()).isEqualTo("{\"status\":\"OK\"}");
        assertThat(saved.getOutcome()).isEqualTo("SUCCESS");
    }

    @Test
    void record_usesSafeMarker_whenPayloadCannotBeSerialized() throws Exception {
        PlanningSession session = session(7L, "traveler@example.com");
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("bad json") { });

        service.record(session, "REFINE", Map.of("messages", List.of()), Map.of("status", "FAILED"), "FAILED");

        ArgumentCaptor<AgentValidationLog> captor = ArgumentCaptor.forClass(AgentValidationLog.class);
        verify(logRepository).save(captor.capture());
        assertThat(captor.getValue().getRequestPayload()).isEqualTo("{\"serializationError\":true}");
        assertThat(captor.getValue().getResponsePayload()).isEqualTo("{\"serializationError\":true}");
    }

    @Test
    void listForAdmin_clampsPaging_andExposesComparisonPayloads() {
        AgentValidationLog log = new AgentValidationLog();
        log.setId(8L);
        log.setUser(session(7L, "traveler@example.com").getUser());
        log.setOperation("REFINE");
        log.setRequestPayload("{\"messages\":[]}");
        log.setResponsePayload("{\"places\":[]}");
        log.setOutcome("SUCCESS");
        log.setCreatedAt(Instant.parse("2026-08-16T06:00:00Z"));
        Page<AgentValidationLog> page = new PageImpl<>(List.of(log), PageRequest.of(0, 100), 1);
        when(logRepository.findAll(any(Pageable.class))).thenReturn(page);

        PageResponse<?> result = service.listForAdmin(-1, 999);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(logRepository).findAll(pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().toString())
                .contains("traveler@example.com", "{\"messages\":[]}", "{\"places\":[]}");
    }

    private PlanningSession session(Long id, String email) {
        User user = new User();
        user.setId(42L);
        user.setEmail(email);
        PlanningSession session = new PlanningSession();
        session.setId(id);
        session.setUser(user);
        return session;
    }
}
