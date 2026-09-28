package com.company.messenger.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import java.util.ArrayList;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class CorsConfig {
    private final WebProperties properties;

    @Bean
    public CorsFilter corsFilter() {
        var source = new UrlBasedCorsConfigurationSource();
        // Cookie endpoints accept only configured browser origins. Electron authenticates in main.
        source.registerCorsConfiguration("/api/v1/auth/**", configuration(properties.allowedOrigins(), true));
        var bearerOrigins = new ArrayList<>(properties.allowedOrigins());
        bearerOrigins.add("null");
        source.registerCorsConfiguration("/**", configuration(bearerOrigins, false));
        return new CorsFilter(source);
    }

    private CorsConfiguration configuration(List<String> origins, boolean credentials) {
        var config = new CorsConfiguration();
        config.setAllowedOriginPatterns(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Internal-Api-Key"));
        config.setAllowCredentials(credentials);
        return config;
    }
}
