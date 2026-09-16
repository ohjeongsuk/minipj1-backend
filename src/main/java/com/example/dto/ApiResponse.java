package com.example.dto;

/**
 * 모든 REST 응답의 공통 봉투.
 * 유일한 예외는 GET /api/v1/data/export 로, CSV 바이트를 직접 반환한다.
 */
public record ApiResponse<T>(boolean success, T data, ErrorBody error) {

    /** 실패 응답의 error 필드 */
    public record ErrorBody(String code, String message) {}

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /** 반환할 데이터가 없는 성공 응답 */
    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, new ErrorBody(code, message));
    }
}
