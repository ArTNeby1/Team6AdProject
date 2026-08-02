package com.loomytrip.backend.service;

import com.loomytrip.backend.dto.response.DestinationResponse;
import com.loomytrip.backend.mapper.EntityMapper;
import com.loomytrip.backend.repository.DestinationRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DestinationService {

    private final DestinationRepository destinationRepository;
    private final EntityMapper entityMapper;

    public DestinationService(DestinationRepository destinationRepository, EntityMapper entityMapper) {
        this.destinationRepository = destinationRepository;
        this.entityMapper = entityMapper;
    }

    @Transactional(readOnly = true)
    public List<DestinationResponse> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return destinationRepository.findAll().stream().limit(50).map(entityMapper::toDestination).toList();
        }
        return destinationRepository.findByNameContainingIgnoreCase(keyword.trim()).stream()
                .map(entityMapper::toDestination)
                .toList();
    }
}
