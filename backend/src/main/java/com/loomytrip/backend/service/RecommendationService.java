package com.loomytrip.backend.service;

import com.loomytrip.backend.dto.response.DestinationResponse;
import com.loomytrip.backend.dto.response.RecommendationResponse;
import com.loomytrip.backend.mapper.EntityMapper;
import com.loomytrip.backend.repository.DestinationRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecommendationService {

    private final DestinationRepository destinationRepository;
    private final EntityMapper entityMapper;

    public RecommendationService(DestinationRepository destinationRepository, EntityMapper entityMapper) {
        this.destinationRepository = destinationRepository;
        this.entityMapper = entityMapper;
    }

    /**
     * Placeholder for F-18 ML recommendation service.
     */
    @Transactional(readOnly = true)
    public RecommendationResponse recommendNearby(Long destinationId) {
        List<DestinationResponse> fallback = destinationRepository.findAll().stream()
                .filter(d -> destinationId == null || !d.getId().equals(destinationId))
                .limit(5)
                .map(entityMapper::toDestination)
                .toList();
        return new RecommendationResponse(fallback);
    }
}
