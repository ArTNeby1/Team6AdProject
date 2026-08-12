package com.loomytrip.backend.service;

import com.loomytrip.backend.client.MapPlacesClient;
import com.loomytrip.backend.dto.response.DestinationResponse;
import com.loomytrip.backend.entity.Destination;
import com.loomytrip.backend.exception.ApiException;
import com.loomytrip.backend.mapper.EntityMapper;
import com.loomytrip.backend.repository.DestinationRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DestinationService {

    private final DestinationRepository destinationRepository;
    private final EntityMapper entityMapper;
    private final MapPlacesClient mapPlacesClient;

    public DestinationService(
            DestinationRepository destinationRepository,
            EntityMapper entityMapper,
            MapPlacesClient mapPlacesClient
    ) {
        this.destinationRepository = destinationRepository;
        this.entityMapper = entityMapper;
        this.mapPlacesClient = mapPlacesClient;
    }

    public static boolean hasUsableCoordinates(BigDecimal lat, BigDecimal lng) {
        if (lat == null || lng == null) {
            return false;
        }
        return lat.compareTo(BigDecimal.ZERO) != 0 || lng.compareTo(BigDecimal.ZERO) != 0;
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
     * Ensures a destination has real coordinates, geocoding and persisting when needed.
     * Never leaves {@code (0,0)} in the database.
     */
    @Transactional
    public Destination ensureGeocoded(Destination destination) {
        if (hasUsableCoordinates(destination.getLatitude(), destination.getLongitude())) {
            return destination;
        }

        var match = mapPlacesClient.validatePlace(destination.getName(), destination.getAddress());
        if (match.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "GEOCODE_FAILED",
                    "Cannot resolve coordinates for: " + destination.getName()
            );
        }

        destination.setLatitude(match.get().latitude());
        destination.setLongitude(match.get().longitude());
        if (match.get().address() != null && !match.get().address().isBlank()) {
            destination.setAddress(match.get().address());
        }
        if (match.get().externalPlaceId() != null) {
            destination.setExternalPlaceId(match.get().externalPlaceId());
        }
        return destinationRepository.save(destination);
    }

    /**
     * Looks up a destination by exact (case-insensitive) name, falling back to the first
     * loose match, and creates one if nothing matches. Coordinates come from the caller,
     * or Nominatim geocoding — never {@link BigDecimal#ZERO}.
     */
    @Transactional
    public Destination findOrCreateByName(String name, String category, BigDecimal latitude, BigDecimal longitude) {
        List<Destination> matches = destinationRepository.findByNameContainingIgnoreCase(name);
        for (Destination candidate : matches) {
            if (candidate.getName().equalsIgnoreCase(name)) {
                if (!hasUsableCoordinates(candidate.getLatitude(), candidate.getLongitude())) {
                    return ensureGeocoded(candidate);
                }
                return candidate;
            }
        }
        if (!matches.isEmpty()) {
            Destination first = matches.get(0);
            if (!hasUsableCoordinates(first.getLatitude(), first.getLongitude())) {
                return ensureGeocoded(first);
            }
            return first;
        }

        BigDecimal resolvedLat = latitude;
        BigDecimal resolvedLng = longitude;
        String resolvedAddress = name;
        if (!hasUsableCoordinates(resolvedLat, resolvedLng)) {
            var match = mapPlacesClient.validatePlace(name, null);
            if (match.isPresent()) {
                resolvedLat = match.get().latitude();
                resolvedLng = match.get().longitude();
                resolvedAddress = match.get().address() != null ? match.get().address() : name;
            }
        }

        if (!hasUsableCoordinates(resolvedLat, resolvedLng)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "GEOCODE_FAILED",
                    "Cannot resolve coordinates for: " + name
            );
        }

        Destination destination = new Destination();
        destination.setName(name);
        destination.setLatitude(resolvedLat);
        destination.setLongitude(resolvedLng);
        destination.setCategory(category);
        destination.setAddress(resolvedAddress);
        return destinationRepository.save(destination);
    }
}
