package com.example.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    private final List<String> allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins}") String allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // 기본값에 의존하면 프리플라이트에서 막히는 경우가 잦아 명시한다
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // CSV 다운로드 시 프론트가 파일명을 읽으려면 필요하다.
        // 빠뜨리면 브라우저가 헤더를 숨겨 파일명이 download 가 된다 (Phase 6 대비)
        config.setExposedHeaders(List.of("Content-Disposition"));
        // 쿠키를 쓰지 않는다. Refresh Token 이 없고 Access Token 은 헤더로 보낸다
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
