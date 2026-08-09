package com.loomytrip.backend.service;

import com.loomytrip.backend.dto.response.DestinationResponse;
import com.loomytrip.backend.entity.Destination;
import com.loomytrip.backend.mapper.EntityMapper;
import com.loomytrip.backend.repository.DestinationRepository;
import java.math.BigDecimal;
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

    /**
     * Looks up a destination by exact (case-insensitive) name, falling back to the first
     * loose match, and creates one if nothing matches. Used by flows that only have a place
     * name to work with (AI recommendations, manually typed itinerary stops) and need a real
     * {@code destination_id} to satisfy {@code trip_schedule}'s FK.
     *
     * <p>Coordinates default to {@link BigDecimal#ZERO} when unknown — {@code destination}'s
     * lat/lng columns are NOT NULL and there's no real geocoding wired up yet
     * ({@code MapPlacesClientStub} is a stub), so this is a known placeholder, not a real fix.
     */
    @Transactional
    public Destination findOrCreateByName(String name, String category, BigDecimal latitude, BigDecimal longitude) {
        List<Destination> matches = destinationRepository.findByNameContainingIgnoreCase(name);
        for (Destination candidate : matches) {
            if (candidate.getName().equalsIgnoreCase(name)) {
                return candidate;
            }
        }
        if (!matches.isEmpty()) {
            return matches.get(0);
        }

        Destination destination = new Destination();
        destination.setName(name);
        destination.setLatitude(latitude != null ? latitude : BigDecimal.ZERO);
        destination.setLongitude(longitude != null ? longitude : BigDecimal.ZERO);
        destination.setCategory(category);
        destination.setAddress(name);
        return destinationRepository.save(destination);
    }
}
