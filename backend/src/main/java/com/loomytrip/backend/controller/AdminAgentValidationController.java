package com.loomytrip.backend.controller;

import com.loomytrip.backend.dto.response.AgentValidationLogResponse;
import com.loomytrip.backend.dto.response.ExtractionEvaluationSummaryResponse;
import com.loomytrip.backend.dto.response.PageResponse;
import com.loomytrip.backend.service.AgentValidationEvalService;
import com.loomytrip.backend.service.AgentValidationLogService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/agent-validations")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminAgentValidationController {

    private final AgentValidationLogService agentValidationLogService;
    private final AgentValidationEvalService agentValidationEvalService;

    public AdminAgentValidationController(
            AgentValidationLogService agentValidationLogService,
            AgentValidationEvalService agentValidationEvalService
    ) {
        this.agentValidationLogService = agentValidationLogService;
        this.agentValidationEvalService = agentValidationEvalService;
    }

    @GetMapping
    public PageResponse<AgentValidationLogResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return agentValidationLogService.listForAdmin(page, size);
    }

    /**
     * LLM Evaluation console (/admin/eval): the four content-level metrics averaged across
     * recent imports, plus each import's per-record breakdown. {@code limit} caps how many
     * recent imports get scored (LLM-as-judge cost control).
     */
    @GetMapping("/evaluations")
    public ExtractionEvaluationSummaryResponse evaluations(
            @RequestParam(defaultValue = "50") int limit
    ) {
        return agentValidationEvalService.summary(limit);
    }
}
