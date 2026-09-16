package com.example.config;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.example.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 인증되지 않은 요청에 401 을 ApiResponse 포맷으로 내려준다.
 *
 * GlobalExceptionHandler 는 @RestControllerAdvice 라 컨트롤러에 진입한 요청만 잡는다.
 * JWT 가 없거나 만료돼 필터 단계에서 거부되면 Spring Security 의 기본 응답이 그대로 나가,
 * "모든 응답이 {success, data, error} 포맷"이라는 규칙이 401 에서 깨진다.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ApiErrorResponseWriter writer;

    public JwtAuthenticationEntryPoint(ApiErrorResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        writer.write(response, ErrorCode.UNAUTHORIZED);
    }
}
