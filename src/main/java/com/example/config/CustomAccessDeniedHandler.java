package com.example.config;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.example.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 인증은 됐지만 권한이 없는 요청에 403 을 ApiResponse 포맷으로 내려준다.
 * (이번 범위에는 역할 구분이 없어 실제로 타지 않지만, 포맷 일관성을 위해 둔다)
 */
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ApiErrorResponseWriter writer;

    public CustomAccessDeniedHandler(ApiErrorResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        writer.write(response, ErrorCode.FORBIDDEN);
    }
}
