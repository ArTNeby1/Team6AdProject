package com.loomytrip.backend.mapper;

import com.loomytrip.backend.dto.response.DestinationResponse;
import com.loomytrip.backend.dto.response.ImportSummaryResponse;
import com.loomytrip.backend.dto.response.PreferenceResponse;
import com.loomytrip.backend.dto.response.TripSummaryResponse;
import com.loomytrip.backend.entity.Destination;
import com.loomytrip.backend.entity.ImportedSource;
import com.loomytrip.backend.entity.Trip;
import com.loomytrip.backend.entity.UserPreference;
import org.springframework.stereotype.Component;

@Component
public class EntityMapper {

    public TripSummaryResponse toTripSummary(Trip trip) {
        return new TripSummaryResponse(
                trip.getId(),
                trip.getTripName(),
                trip.getStartDate(),
                trip.getDurationDays(),
                trip.getUpdatedAt()
        );
    }

    public ImportSummaryResponse toImportSummary(ImportedSource source) {
        return new ImportSummaryResponse(
                source.getId(),
                source.getSourceType(),
                source.getTitle(),
                source.getStatus(),
                source.getCreatedAt()
        );
    }

    public DestinationResponse toDestination(Destination destination) {
        return new DestinationResponse(
                destination.getId(),
                destination.getName(),
                destination.getAddress(),
                destination.getLatitude(),
                destination.getLongitude(),
                destination.getCategory()
        );
    }

    public PreferenceResponse toPreference(UserPreference preference) {
        return new PreferenceResponse(preference.getPreferenceKey(), preference.getPreferenceValue());
    }
}
