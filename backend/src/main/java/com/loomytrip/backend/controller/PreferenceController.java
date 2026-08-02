package com.loomytrip.backend.controller;

import com.loomytrip.backend.dto.request.UpsertPreferenceRequest;
import com.loomytrip.backend.dto.response.PreferenceResponse;
import com.loomytrip.backend.service.PreferenceService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/preferences")
public class PreferenceController {

    private final PreferenceService preferenceService;

    public PreferenceController(PreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public List<PreferenceResponse> listPreferences() {
        return preferenceService.listMyPreferences();
    }

    @PutMapping
    public PreferenceResponse upsert(@Valid @RequestBody UpsertPreferenceRequest request) {
        return preferenceService.upsert(request);
    }
}
