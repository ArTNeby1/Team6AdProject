package com.loomytrip.backend.controller;

import com.loomytrip.backend.dto.response.AdminUserResponse;
import com.loomytrip.backend.dto.response.PageResponse;
import com.loomytrip.backend.service.AdminUserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin console: read-only traveler user list (S1).
 * Method-level {@link PreAuthorize} is a second gate on top of the route-level
 * rule in {@code SecurityConfig}; either alone would suffice, both together are
 * defense in depth.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public PageResponse<AdminUserResponse> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String q
    ) {
        return adminUserService.listUsers(page, size, q);
    }
}
