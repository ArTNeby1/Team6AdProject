package com.loomytrip.backend.dto.response;

import java.util.List;

public record RecommendationResponse(
        List<RecommendationItemResponse> items
) {
}
