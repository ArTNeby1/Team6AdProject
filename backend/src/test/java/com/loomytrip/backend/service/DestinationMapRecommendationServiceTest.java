package com.loomytrip.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loomytrip.backend.client.AiPlanningClient;
import com.loomytrip.backend.client.AiRecommendResult;
import com.loomytrip.backend.client.MapPlacesClient;
import com.loomytrip.backend.dto.response.DestinationResponse;
import com.loomytrip.backend.dto.response.MapConfigResponse;
import com.loomytrip.backend.dto.response.RecommendationResponse;
import com.loomytrip.backend.entity.Destination;
import com.loomytrip.backend.exception.ApiException;
import com.loomytrip.backend.mapper.EntityMapper;
import com.loomytrip.backend.repository.DestinationRepository;
import com.loomytrip.backend.repository.TripScheduleRepository;
import com.loomytrip.backend.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DestinationMapRecommendationServiceTest {

    @Mock private DestinationRepository destinationRepository;
    @Mock private EntityMapper entityMapper;
    @Mock private MapPlacesClient mapPlacesClient;
    @Mock private UserRepository userRepository;
    @Mock private TripScheduleRepository tripScheduleRepository;
    @Mock private AiPlanningClient aiPlanningClient;
    @Mock private DestinationService destinationService;

    @Test
    void hasUsableCoordinates_rejectsNullAndZeroZero() {
        assertThat(DestinationService.hasUsableCoordinates(null, BigDecimal.ONE)).isFalse();
        assertThat(DestinationService.hasUsableCoordinates(BigDecimal.ZERO, BigDecimal.ZERO)).isFalse();
        assertThat(DestinationService.hasUsableCoordinates(new BigDecimal("1.28"), new BigDecimal("103.85")))
                .isTrue();
    }

    @Test
    void search_blankReturnsLimitedAll_andKeywordUsesContains() {
        DestinationService service = new DestinationService(destinationRepository, entityMapper, mapPlacesClient);
        Destination destination = destination(1L, "MBS", "1.28", "103.85");
        when(destinationRepository.findAll()).thenReturn(List.of(destination));
        when(entityMapper.toDestination(destination))
                .thenReturn(new DestinationResponse(1L, "MBS", null, null, null, null));

        assertThat(service.search("  ")).hasSize(1);

        when(destinationRepository.findByNameContainingIgnoreCase("marina"))
                .thenReturn(List.of(destination));
        assertThat(service.search("marina")).hasSize(1);
    }

    @Test
    void ensureGeocoded_reusesSingaporeCoords_andFailsWhenLookupMisses() {
        DestinationService service = new DestinationService(destinationRepository, entityMapper, mapPlacesClient);
        Destination ok = destination(1L, "MBS", "1.28", "103.85");
        assertThat(service.ensureGeocoded(ok)).isSameAs(ok);

        Destination bad = destination(2L, "Chinatown", "40.71", "-74.00");
        when(mapPlacesClient.validatePlace("Chinatown", null)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.ensureGeocoded(bad))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("GEOCODE_FAILED");
    }

    @Test
    void findOrCreateByName_returnsExactMatch_andCreatesWhenMissing() {
        DestinationService service = new DestinationService(destinationRepository, entityMapper, mapPlacesClient);
        Destination existing = destination(1L, "MBS", "1.28", "103.85");
        when(destinationRepository.findByNameContainingIgnoreCase("MBS")).thenReturn(List.of(existing));

        assertThat(service.findOrCreateByName("MBS", "attraction", null, null)).isSameAs(existing);

        when(destinationRepository.findByNameContainingIgnoreCase("Sentosa")).thenReturn(List.of());
        when(mapPlacesClient.validatePlace("Sentosa", null)).thenReturn(Optional.of(
                new MapPlacesClient.PlaceMatch(
                        "Sentosa", "Sentosa, Singapore",
                        new BigDecimal("1.25"), new BigDecimal("103.83"), "osm-1"
                )
        ));
        when(destinationRepository.save(any(Destination.class))).thenAnswer(inv -> inv.getArgument(0));

        Destination created = service.findOrCreateByName("Sentosa", "attraction", null, null);
        assertThat(created.getName()).isEqualTo("Sentosa");
        assertThat(created.getLatitude()).isEqualByComparingTo("1.25");
    }

    @Test
    void mapService_returnsConfig_andDelegatesNearby() {
        RecommendationService recommendationService = org.mockito.Mockito.mock(RecommendationService.class);
        MapService mapService = new MapService(
                recommendationService,
                "https://tiles/{z}/{x}/{y}.png",
                "attr",
                1.35,
                103.82,
                11
        );
        RecommendationResponse nearby = new RecommendationResponse(List.of());
        when(recommendationService.recommendNearCoordinates(1.3, 103.8, "hybrid", 5)).thenReturn(nearby);

        MapConfigResponse config = mapService.getConfig();
        assertThat(config.tileUrlTemplate()).contains("tiles");
        assertThat(config.defaultZoom()).isEqualTo(11);
        assertThat(mapService.nearby(1.3, 103.8, "hybrid", 5)).isSameAs(nearby);
    }

    @Test
    void recommendationService_fallsBackWhenDestinationMissingOrAiUnavailable() {
        RecommendationService service = new RecommendationService(
                destinationRepository, userRepository, tripScheduleRepository, aiPlanningClient, destinationService
        );
        Destination fallback = destination(9L, "Fallback", "1.3", "103.8");
        when(destinationRepository.findAll()).thenReturn(List.of(fallback));

        RecommendationResponse withoutId = service.recommendNearby(null, null, null);
        assertThat(withoutId.items()).hasSize(1);

        when(destinationRepository.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.recommendNearby(1L, "hybrid", 3))
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);

        Destination anchor = destination(1L, "Anchor", "1.28", "103.85");
        when(destinationRepository.findById(1L)).thenReturn(Optional.of(anchor));
        when(destinationService.ensureGeocoded(anchor)).thenReturn(anchor);
        when(aiPlanningClient.recommend(any(), isNull(), isNull(), eq("hybrid"), eq(5), isNull(), eq("Singapore")))
                .thenReturn(new AiRecommendResult("UNAVAILABLE", null, List.of(), List.of()));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("a@example.com", "n/a", List.of())
        );
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.empty());

        RecommendationResponse fallbackResult = service.recommendNearby(1L, " ", 0);
        assertThat(fallbackResult.items()).extracting(item -> item.name()).containsExactly("Fallback");
        SecurityContextHolder.clearContext();
    }

    @Test
    void recommendationService_mapsAiSuggestions_andFiltersVisited() {
        RecommendationService service = new RecommendationService(
                destinationRepository, userRepository, tripScheduleRepository, aiPlanningClient, destinationService
        );
        Destination anchor = destination(1L, "Anchor", "1.28", "103.85");
        when(destinationRepository.findById(1L)).thenReturn(Optional.of(anchor));
        when(destinationService.ensureGeocoded(anchor)).thenReturn(anchor);
        when(aiPlanningClient.recommend(any(), isNull(), isNull(), eq("hybrid"), eq(3), isNull(), eq("Singapore")))
                .thenReturn(new AiRecommendResult(
                        "OK",
                        null,
                        List.of(),
                        List.of(
                                new AiRecommendResult.SuggestedAddition(
                                        "Visited Place", "attraction",
                                        new BigDecimal("1.29"), new BigDecimal("103.86"),
                                        1.2, 0.9, "near", List.of("walk")
                                ),
                                new AiRecommendResult.SuggestedAddition(
                                        "New Place", "food",
                                        new BigDecimal("1.30"), new BigDecimal("103.87"),
                                        2.0, 0.8, "tasty", List.of()
                                )
                        )
                ));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("a@example.com", "n/a", List.of())
        );
        var user = new com.loomytrip.backend.entity.User();
        user.setId(1L);
        user.setEmail("a@example.com");
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(user));
        when(tripScheduleRepository.findVisitedDestinationNamesByUserId(1L))
                .thenReturn(Set.of("visited place"));
        when(destinationRepository.findByNameContainingIgnoreCase("New Place")).thenReturn(List.of());

        RecommendationResponse response = service.recommendNearby(1L, "hybrid", 3);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).name()).isEqualTo("New Place");
        SecurityContextHolder.clearContext();
    }

    private static Destination destination(Long id, String name, String lat, String lng) {
        Destination destination = new Destination();
        destination.setId(id);
        destination.setName(name);
        destination.setLatitude(new BigDecimal(lat));
        destination.setLongitude(new BigDecimal(lng));
        return destination;
    }
}
