package com.example.exception;

import org.springframework.http.HttpStatus;

/**
 * CLAUDE.md 11장 표 전체.
 * NOT_FOUND 와 TRANSACTION_NOT_FOUND 를 겸용하지 않는다. 둘 다 404 지만 메시지가 다르다.
 */
public enum ErrorCode {

    // 400
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    CATEGORY_TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "거래 구분과 카테고리 구분이 일치하지 않습니다."),
    INVALID_CSV(HttpStatus.BAD_REQUEST, "CSV 형식이 올바르지 않습니다."),

    // 401
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),

    // 403
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    // 404
    TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "거래 내역을 찾을 수 없습니다."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다."),

    // 405
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),

    // 415
    /** multipart 를 받는 엔드포인트에 다른 Content-Type 으로 들어온 경우 */
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 Content-Type 입니다."),

    // 409
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    /** 구글 로그인인데 같은 이메일의 로컬 계정이 이미 있다. 자동 연동하지 않는다 (AUTH-09) */
    EMAIL_CONFLICT(HttpStatus.CONFLICT, "이미 이메일로 가입된 계정입니다. 비밀번호로 로그인해 주세요."),
    CATEGORY_DUPLICATED(HttpStatus.CONFLICT, "이미 존재하는 카테고리 이름입니다."),

    // 500
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
