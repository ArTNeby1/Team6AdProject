package com.loomytrip.backend.controller;

import com.loomytrip.backend.dto.response.AgentValidationLogResponse;
import com.loomytrip.backend.dto.response.PageResponse;
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

    public AdminAgentValidationController(AgentValidationLogService agentValidationLogService) {
        this.agentValidationLogService = agentValidationLogService;
    }

    @GetMapping
    public PageResponse<AgentValidationLogResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return agentValidationLogService.listForAdmin(page, size);
    }
}
