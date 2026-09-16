package com.example.config;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Phase 1 골격.
 *
 * starter-security 가 이미 클래스패스에 있어, 이 빈이 없으면 Boot 가 기본 보안을 자동 구성해
 * 모든 경로에 폼 로그인을 건다. 그러면 Swagger UI 접속부터 막힌다.
 *
 * Phase 3 에서 JwtAuthenticationFilter / EntryPoint / AccessDeniedHandler 를 붙이고
 * anyRequest() 를 authenticated() 로 조인다. 지금은 전부 permitAll 이다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // JWT stateless API 다. CSRF 토큰을 발급하는 경로 자체가 없다.
                // 이 줄이 없으면 POST /api/v1/auth/signup 부터 403 으로 막힌다.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
                // Spring Security 7 에서 authorizeRequests() 는 제거되었다. authorizeHttpRequests() 를 쓴다.
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
