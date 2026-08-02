package com.loomytrip.backend.service;

import com.loomytrip.backend.client.AiPlanningClient;
import com.loomytrip.backend.client.MapPlacesClient;
import com.loomytrip.backend.dto.request.CreateImportRequest;
import com.loomytrip.backend.dto.request.UpdateExtractedPlaceRequest;
import com.loomytrip.backend.dto.response.ImportSummaryResponse;
import com.loomytrip.backend.entity.ExtractedPlace;
import com.loomytrip.backend.entity.ImportStatus;
import com.loomytrip.backend.entity.ImportedSource;
import com.loomytrip.backend.entity.User;
import com.loomytrip.backend.exception.ApiException;
import com.loomytrip.backend.mapper.EntityMapper;
import com.loomytrip.backend.repository.ExtractedPlaceRepository;
import com.loomytrip.backend.repository.ImportedSourceRepository;
import com.loomytrip.backend.repository.UserRepository;
import com.loomytrip.backend.util.SecurityUtils;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportService {

    private final ImportedSourceRepository importedSourceRepository;
    private final ExtractedPlaceRepository extractedPlaceRepository;
    private final UserRepository userRepository;
    private final EntityMapper entityMapper;
    private final AiPlanningClient aiPlanningClient;
    private final MapPlacesClient mapPlacesClient;

    public ImportService(
            ImportedSourceRepository importedSourceRepository,
            ExtractedPlaceRepository extractedPlaceRepository,
            UserRepository userRepository,
            EntityMapper entityMapper,
            AiPlanningClient aiPlanningClient,
            MapPlacesClient mapPlacesClient
    ) {
        this.importedSourceRepository = importedSourceRepository;
        this.extractedPlaceRepository = extractedPlaceRepository;
        this.userRepository = userRepository;
        this.entityMapper = entityMapper;
        this.aiPlanningClient = aiPlanningClient;
        this.mapPlacesClient = mapPlacesClient;
    }

    @Transactional(readOnly = true)
    public List<ImportSummaryResponse> listMyImports() {
        User user = currentUser();
        return importedSourceRepository.findByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(entityMapper::toImportSummary)
                .toList();
    }

    @Transactional
    public ImportSummaryResponse createImport(CreateImportRequest request) {
        User user = currentUser();
        ImportedSource source = new ImportedSource();
        source.setUser(user);
        source.setSourceType(request.sourceType());
        source.setTitle(request.title());
        source.setRawContent(request.rawContent());
        source.setSourceUrl(request.sourceUrl());
        source.setStatus(ImportStatus.PENDING);
        return entityMapper.toImportSummary(importedSourceRepository.save(source));
    }

    /**
     * Placeholder for F-03 extraction via AI agent.
     */
    public Object runExtraction(Long importId) {
        loadOwnedImport(importId);
        aiPlanningClient.extractTravelInfo(null, null);
        throw new ApiException(
                HttpStatus.NOT_IMPLEMENTED,
                "NOT_IMPLEMENTED",
                "Extraction pipeline will persist extracted_place/activity in a later iteration"
        );
    }

    /**
     * Placeholder for F-05 map validation.
     */
    public Object validatePlaces(Long importId) {
        loadOwnedImport(importId);
        mapPlacesClient.validatePlace(null, null);
        throw new ApiException(
                HttpStatus.NOT_IMPLEMENTED,
                "NOT_IMPLEMENTED",
                "Place validation will update validation_status in a later iteration"
        );
    }

    @Transactional
    public void updateExtractedPlace(Long placeId, UpdateExtractedPlaceRequest request) {
        ExtractedPlace place = extractedPlaceRepository.findById(placeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLACE_NOT_FOUND", "Extracted place not found"));
        ensureImportOwner(place.getImportedSource());

        if (request.name() != null) {
            place.setName(request.name());
        }
        if (request.address() != null) {
            place.setAddress(request.address());
        }
        if (request.latitude() != null) {
            place.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            place.setLongitude(request.longitude());
        }
        if (request.category() != null) {
            place.setCategory(request.category());
        }
        if (request.note() != null) {
            place.setNote(request.note());
        }
        extractedPlaceRepository.save(place);
    }

    @Transactional
    public void deleteExtractedPlace(Long placeId) {
        ExtractedPlace place = extractedPlaceRepository.findById(placeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PLACE_NOT_FOUND", "Extracted place not found"));
        ensureImportOwner(place.getImportedSource());
        extractedPlaceRepository.delete(place);
    }

    private ImportedSource loadOwnedImport(Long importId) {
        ImportedSource source = importedSourceRepository.findById(importId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "IMPORT_NOT_FOUND", "Import not found"));
        ensureImportOwner(source);
        return source;
    }

    private void ensureImportOwner(ImportedSource source) {
        User user = currentUser();
        if (!source.getUser().getId().equals(user.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Import does not belong to current user");
        }
    }

    private User currentUser() {
        return userRepository.findByEmail(SecurityUtils.currentUserEmail())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "User not found"));
    }
}
