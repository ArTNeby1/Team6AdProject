package com.loomytrip.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "loomytrip.cors")
public record CorsProperties(String allowedOrigins) {
}
