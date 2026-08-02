package com.loomytrip.backend.controller;

import com.loomytrip.backend.dto.request.CreateImportRequest;
import com.loomytrip.backend.dto.request.UpdateExtractedPlaceRequest;
import com.loomytrip.backend.dto.response.ImportSummaryResponse;
import com.loomytrip.backend.service.ImportService;
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
@RequestMapping("/api/v1/imports")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @GetMapping
    public List<ImportSummaryResponse> listImports() {
        return importService.listMyImports();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ImportSummaryResponse createImport(@Valid @RequestBody CreateImportRequest request) {
        return importService.createImport(request);
    }

    @PostMapping("/{importId}/extract")
    public Object extract(@PathVariable Long importId) {
        return importService.runExtraction(importId);
    }

    @PostMapping("/{importId}/validate-places")
    public Object validatePlaces(@PathVariable Long importId) {
        return importService.validatePlaces(importId);
    }

    @PutMapping("/places/{placeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updatePlace(@PathVariable Long placeId, @RequestBody UpdateExtractedPlaceRequest request) {
        importService.updateExtractedPlace(placeId, request);
    }

    @DeleteMapping("/places/{placeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePlace(@PathVariable Long placeId) {
        importService.deleteExtractedPlace(placeId);
    }
}
