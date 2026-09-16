package com.example.config;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.example.service.CustomOidcUserService;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;
    private final CorsConfigurationSource corsConfigurationSource;
    private final CustomOidcUserService oidcUserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final OAuth2FailureHandler oAuth2FailureHandler;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          JwtAuthenticationEntryPoint authenticationEntryPoint,
                          CustomAccessDeniedHandler accessDeniedHandler,
                          CorsConfigurationSource corsConfigurationSource,
                          CustomOidcUserService oidcUserService,
                          OAuth2SuccessHandler oAuth2SuccessHandler,
                          OAuth2FailureHandler oAuth2FailureHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.corsConfigurationSource = corsConfigurationSource;
        this.oidcUserService = oidcUserService;
        this.oAuth2SuccessHandler = oAuth2SuccessHandler;
        this.oAuth2FailureHandler = oAuth2FailureHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // JWT stateless API 다. CSRF 토큰을 발급하는 경로 자체가 없다.
                // 이 줄이 없으면 POST /api/v1/auth/signup 부터 403 으로 막힌다.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                /*
                 * ⚠️ 여전히 STATELESS 다. SecurityContext 는 세션에 저장하지 않는다.
                 *    다만 OAuth2 인가 요청의 state 는 Spring Security 가 세션에 잠시 담는다
                 *    (HttpSessionOAuth2AuthorizationRequestRepository). 이건 인증이 끝나면
                 *    버려지는 임시 값이라 "요청마다 JWT 로 인증한다"는 원칙과 충돌하지 않는다.
                 */
                .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
                // Spring Security 7 에서 authorizeRequests() 는 제거되었다. authorizeHttpRequests() 를 쓴다.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/signup",
                                "/api/v1/auth/login",
                                // ⚠️ Swagger 경로를 빼면 Phase 1 의 DoD 가 여기서 조용히 회귀한다
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/error",
                                // 구글 로그인 시작(/oauth2/authorization/google)과 콜백(/login/oauth2/code/google)
                                "/oauth2/**",
                                "/login/oauth2/**"
                        ).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // 구글 로그인 (AUTH-09). 인증이 끝나면 우리 JWT 를 발급해 프론트로 리다이렉트한다
                .oauth2Login(oauth2 -> oauth2
                        /*
                         * ⚠️ oidcUserService 로 등록한다. scope 에 openid 가 있으면 OIDC 흐름이라
                         *    userInfoEndpoint().userService(...) 에 등록한 OAuth2UserService 는 호출되지 않는다.
                         *    그러면 우리 코드가 조용히 건너뛰어지고 SuccessHandler 에서 터진다.
                         */
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService))
                        .successHandler(oAuth2SuccessHandler)
                        .failureHandler(oAuth2FailureHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

}
