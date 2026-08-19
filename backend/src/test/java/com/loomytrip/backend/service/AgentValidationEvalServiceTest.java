package com.loomytrip.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomytrip.backend.client.AiPlanningClient;
import com.loomytrip.backend.dto.response.ExtractionEvaluationResponse;
import com.loomytrip.backend.dto.response.ExtractionEvaluationSummaryResponse;
import com.loomytrip.backend.entity.AgentValidationLog;
import com.loomytrip.backend.entity.User;
import com.loomytrip.backend.repository.AgentValidationLogRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AgentValidationEvalServiceTest {

    @Mock
    private AgentValidationLogRepository logRepository;

    @Mock
    private AiPlanningClient aiPlanningClient;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AgentValidationEvalService service;

    @Test
    void summary_parsesPayloads_averagesScoredImports_andCachesPerLog() {
        AgentValidationLog first = importLog(1L, "a@example.com",
                "{\"raw_content\":\"Gardens by the Bay and Merlion Park\"}",
                "{\"places\":[{\"name\":\"Gardens by the Bay\"},{\"name\":\"Merlion Park\"}]}");
        AgentValidationLog second = importLog(2L, "b@example.com",
                "{\"raw_content\":\"Marina Bay Sands\"}",
                "{\"places\":[{\"name\":\"Marina Bay Sands\"}]}");
        when(logRepository.findByOperationAndOutcomeOrderByIdDesc(eq("IMPORT"), eq("SUCCESS"), any(Pageable.class)))
                .thenReturn(List.of(first, second));

        // The source text carried into the judge must be the raw_content, and the predicted
        // places must be the names lifted out of responsePayload.places[].
        when(aiPlanningClient.evaluateExtraction(
                eq("Gardens by the Bay and Merlion Park"),
                eq(List.of("Gardens by the Bay", "Merlion Park"))))
                .thenReturn(Map.of(
                        "precision", 1.0, "recall", 1.0, "f1", 1.0, "groundedness", 1.0,
                        "gold_places", List.of("Gardens by the Bay", "Merlion Park"),
                        "predicted_places", List.of("Gardens by the Bay", "Merlion Park"),
                        "matched", List.of("Gardens by the Bay", "Merlion Park"),
                        "missed", List.of(), "spurious", List.of()));
        when(aiPlanningClient.evaluateExtraction(eq("Marina Bay Sands"), eq(List.of("Marina Bay Sands"))))
                .thenReturn(Map.of(
                        "precision", 0.5, "recall", 0.0, "f1", 0.0, "groundedness", 1.0,
                        "gold_places", List.of("Marina Bay Sands", "Sentosa"),
                        "predicted_places", List.of("Marina Bay Sands"),
                        "matched", List.of(), "missed", List.of("Sentosa"),
                        "spurious", List.of()));

        ExtractionEvaluationSummaryResponse summary = service.summary(50);

        assertThat(summary.totalCount()).isEqualTo(2);
        assertThat(summary.scoredCount()).isEqualTo(2);
        assertThat(summary.averages().precision()).isEqualTo(0.75); // (1.0 + 0.5) / 2
        assertThat(summary.averages().recall()).isEqualTo(0.5);     // (1.0 + 0.0) / 2
        assertThat(summary.records()).hasSize(2);
        ExtractionEvaluationResponse missedRecord = summary.records().get(1);
        assertThat(missedRecord.available()).isTrue();
        assertThat(missedRecord.missed()).containsExactly("Sentosa");

        // First summary scores each of the two logs once; the second summary is served
        // entirely from cache, so the judge is still only invoked twice in total.
        service.summary(50);
        verify(aiPlanningClient, times(2)).evaluateExtraction(anyString(), anyList());
    }

    @Test
    void summary_marksUnavailableWhenJudgeDown_andLeavesItOutOfAverages() {
        AgentValidationLog scored = importLog(1L, "a@example.com",
                "{\"raw_content\":\"Merlion Park\"}",
                "{\"places\":[{\"name\":\"Merlion Park\"}]}");
        AgentValidationLog down = importLog(2L, "b@example.com",
                "{\"raw_content\":\"Sentosa\"}",
                "{\"places\":[{\"name\":\"Sentosa\"}]}");
        when(logRepository.findByOperationAndOutcomeOrderByIdDesc(anyString(), anyString(), any(Pageable.class)))
                .thenReturn(List.of(scored, down));
        when(aiPlanningClient.evaluateExtraction(eq("Merlion Park"), anyList()))
                .thenReturn(Map.of("precision", 1.0, "recall", 1.0, "f1", 1.0, "groundedness", 1.0));
        when(aiPlanningClient.evaluateExtraction(eq("Sentosa"), anyList()))
                .thenReturn(Map.of("status", "AI_SERVICE_UNAVAILABLE"));

        ExtractionEvaluationSummaryResponse summary = service.summary(50);

        assertThat(summary.totalCount()).isEqualTo(2);
        assertThat(summary.scoredCount()).isEqualTo(1);
        assertThat(summary.averages().precision()).isEqualTo(1.0);
        assertThat(summary.records().get(1).available()).isFalse();
        assertThat(summary.records().get(1).precision()).isNull();
    }

    private AgentValidationLog importLog(Long id, String email, String request, String response) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        AgentValidationLog log = new AgentValidationLog();
        log.setId(id);
        log.setUser(user);
        log.setOperation("IMPORT");
        log.setOutcome("SUCCESS");
        log.setRequestPayload(request);
        log.setResponsePayload(response);
        log.setCreatedAt(Instant.parse("2026-08-16T06:00:00Z"));
        return log;
    }
}
