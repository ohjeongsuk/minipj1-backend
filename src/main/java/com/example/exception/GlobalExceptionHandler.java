package com.example.exception;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.example.dto.ApiResponse;

/**
 * 예외 응답은 이 클래스 한 곳에서만 만든다. 컨트롤러에서 개별 try-catch 로 응답을 조립하지 않는다.
 *
 * 주의 1) 아래 구체 핸들러들이 없으면 맨 끝의 catch-all 이 Spring MVC 표준 예외까지 삼켜 500 으로 내보낸다.
 * 주의 2) ArithmeticException 은 의도적으로 매핑하지 않는다.
 *         BigDecimal.divide 규칙을 지키면 발생하지 않아야 하고, 매핑해두면 계산 버그가 400 으로 위장된다.
 * 주의 3) 이 클래스는 @RestControllerAdvice 라 컨트롤러에 진입한 요청만 잡는다.
 *         Security 필터 단계의 401/403 은 config 의 EntryPoint/AccessDeniedHandler 가 처리한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 서비스 계층이 의도적으로 던진 예외 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        return build(code, e.getMessage());
    }

    /** @Valid 검증 실패 — 필드별 메시지를 합쳐서 내려준다 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining(", "));
        return build(ErrorCode.INVALID_INPUT,
                message.isBlank() ? ErrorCode.INVALID_INPUT.getMessage() : message);
    }

    /**
     * 안전장치. BCryptPasswordEncoder 는 72바이트를 넘는 비밀번호에
     * IllegalArgumentException 을 던지므로, 매핑이 없으면 500 으로 나간다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("잘못된 인자: {}", e.getMessage());
        return build(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.getMessage());
    }

    /** 없는 API 경로. catch-all 보다 먼저 잡아야 500 으로 흘러가지 않는다 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        return build(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.getMessage());
    }

    /** 지원하지 않는 HTTP 메서드. 위와 같은 이유로 명시적으로 잡는다 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return build(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getMessage());
    }

    /** 최후의 방어선. 스택트레이스와 내부 메시지는 클라이언트에 노출하지 않는다 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage());
    }

    private String formatFieldError(FieldError error) {
        String message = error.getDefaultMessage();
        return error.getField() + ": " + (message == null ? "값이 올바르지 않습니다." : message);
    }

    private ResponseEntity<ApiResponse<Void>> build(ErrorCode code, String message) {
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.error(code.name(), message));
    }
}
