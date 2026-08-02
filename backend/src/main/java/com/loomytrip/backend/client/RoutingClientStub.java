package com.loomytrip.backend.client;

import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class RoutingClientStub implements RoutingClient {

    @Override
    public Optional<RouteEstimate> estimate(
            BigDecimal fromLat,
            BigDecimal fromLng,
            BigDecimal toLat,
            BigDecimal toLng
    ) {
        return Optional.empty();
    }
}
