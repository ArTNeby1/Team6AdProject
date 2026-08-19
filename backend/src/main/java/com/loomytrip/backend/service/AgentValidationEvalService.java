package com.loomytrip.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomytrip.backend.client.AiPlanningClient;
import com.loomytrip.backend.dto.response.ExtractionEvaluationResponse;
import com.loomytrip.backend.dto.response.ExtractionEvaluationSummaryResponse;
import com.loomytrip.backend.entity.AgentValidationLog;
import com.loomytrip.backend.repository.AgentValidationLogRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backs the admin LLM Evaluation console (/admin/eval): scores each import's extraction for
 * content-level accuracy via the ML LLM-as-judge, and averages the four metrics across all
 * scored imports.
 *
 * <p>Scores are cached in memory keyed by log id. An {@link AgentValidationLog}'s payloads are
 * immutable once written and the judge runs at temperature 0, so a log's score never changes —
 * caching turns a page reload from N LLM calls back into zero. The cache is deliberately not
 * persisted: on restart it simply rebuilds, which keeps this feature migration-free.
 */
@Service
public class AgentValidationEvalService {

    /** Upper bound on how many recent imports one summary call will score, to cap LLM cost. */
    private static final int MAX_RECORDS = 50;

    private final AgentValidationLogRepository logRepository;
    private final AiPlanningClient aiPlanningClient;
    private final ObjectMapper objectMapper;

    private final Map<Long, ExtractionEvaluationResponse> cache = new ConcurrentHashMap<>();

    public AgentValidationEvalService(
            AgentValidationLogRepository logRepository,
            AiPlanningClient aiPlanningClient,
            ObjectMapper objectMapper
    ) {
        this.logRepository = logRepository;
        this.aiPlanningClient = aiPlanningClient;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ExtractionEvaluationSummaryResponse summary(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_RECORDS);
        List<AgentValidationLog> imports = logRepository.findByOperationAndOutcomeOrderByIdDesc(
                "IMPORT", "SUCCESS", PageRequest.of(0, safeLimit));

        List<ExtractionEvaluationResponse> records = new ArrayList<>(imports.size());
        double sumPrecision = 0, sumRecall = 0, sumF1 = 0, sumGrounded = 0;
        int scored = 0;
        for (AgentValidationLog log : imports) {
            ExtractionEvaluationResponse record = evaluate(log);
            records.add(record);
            if (record.available()) {
                scored++;
                sumPrecision += record.precision();
                sumRecall += record.recall();
                sumF1 += record.f1();
                sumGrounded += record.groundedness();
            }
        }

        ExtractionEvaluationSummaryResponse.Averages averages = scored == 0
                ? new ExtractionEvaluationSummaryResponse.Averages(null, null, null, null)
                : new ExtractionEvaluationSummaryResponse.Averages(
                        sumPrecision / scored,
                        sumRecall / scored,
                        sumF1 / scored,
                        sumGrounded / scored);

        return new ExtractionEvaluationSummaryResponse(imports.size(), scored, averages, records);
    }

    /** Score one import, reusing the cached result when present. */
    public ExtractionEvaluationResponse evaluate(AgentValidationLog log) {
        ExtractionEvaluationResponse cached = cache.get(log.getId());
        if (cached != null) {
            return cached;
        }
        ExtractionEvaluationResponse result = computeEvaluation(log);
        cache.put(log.getId(), result);
        return result;
    }

    private ExtractionEvaluationResponse computeEvaluation(AgentValidationLog log) {
        String sourceText = readSourceText(log.getRequestPayload());
        List<String> predicted = readPredictedPlaces(log.getResponsePayload());

        Map<String, Object> scored = aiPlanningClient.evaluateExtraction(sourceText, predicted);
        boolean available = scored != null && scored.containsKey("precision");

        if (!available) {
            return new ExtractionEvaluationResponse(
                    log.getId(), log.getUser().getEmail(), log.getOperation(), log.getCreatedAt(),
                    false, null, null, null, null,
                    sourceText, predicted, List.of(), List.of(), List.of(), List.of());
        }

        return new ExtractionEvaluationResponse(
                log.getId(), log.getUser().getEmail(), log.getOperation(), log.getCreatedAt(),
                true,
                toDouble(scored.get("precision")),
                toDouble(scored.get("recall")),
                toDouble(scored.get("f1")),
                toDouble(scored.get("groundedness")),
                sourceText,
                toStringList(scored.get("predicted_places"), predicted),
                toStringList(scored.get("gold_places"), List.of()),
                toStringList(scored.get("matched"), List.of()),
                toStringList(scored.get("missed"), List.of()),
                toStringList(scored.get("spurious"), List.of()));
    }

    /** requestPayload is {@code {"raw_content": "...", "source_url": null}} — pull the text. */
    private String readSourceText(String requestPayload) {
        JsonNode node = readTree(requestPayload);
        if (node == null) {
            return "";
        }
        JsonNode raw = node.get("raw_content");
        return raw != null && !raw.isNull() ? raw.asText("") : "";
    }

    /** responsePayload is the extraction result — collect {@code places[].name}. */
    private List<String> readPredictedPlaces(String responsePayload) {
        JsonNode node = readTree(responsePayload);
        List<String> names = new ArrayList<>();
        if (node == null) {
            return names;
        }
        JsonNode places = node.get("places");
        if (places != null && places.isArray()) {
            for (JsonNode place : places) {
                JsonNode name = place.get("name");
                if (name != null && !name.isNull() && !name.asText().isBlank()) {
                    names.add(name.asText().trim());
                }
            }
        }
        return names;
    }

    private JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return null;
        }
    }

    private Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<String> toStringList(Object value, List<String> fallback) {
        if (value instanceof List<?> list) {
            return objectMapper.convertValue(list, new TypeReference<List<String>>() {});
        }
        return fallback;
    }
}
