package com.loomytrip.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
public class AppConfig {
}
//cicd demonstration