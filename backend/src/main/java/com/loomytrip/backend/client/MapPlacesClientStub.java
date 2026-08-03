package com.loomytrip.backend.client;

import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class MapPlacesClientStub implements MapPlacesClient {

    @Override
    public Optional<PlaceMatch> validatePlace(String name, String address) {
        return Optional.empty();
    }
}
