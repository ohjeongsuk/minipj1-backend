package com.example.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.example.dto.ApiResponse;
import com.example.exception.ErrorCode;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Security 필터 단계의 예외 응답을 ApiResponse 포맷으로 직접 write 한다.
 * EntryPoint(401)와 AccessDeniedHandler(403)가 같은 코드를 쓰지 않도록 한 곳에 모았다.
 *
 * Spring Boot 4 의 직렬화 엔진은 Jackson 3(tools.jackson)다.
 * 클래스패스에는 springdoc/jjwt-jackson 이 끌고 온 Jackson 2(com.fasterxml.jackson)도 있으므로
 * import 를 혼동하면 애플리케이션 전역과 다른 설정으로 직렬화된다.
 */
@Component
public class ApiErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ApiErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                objectMapper.writeValueAsString(
                        ApiResponse.error(errorCode.name(), errorCode.getMessage())));
    }
}
