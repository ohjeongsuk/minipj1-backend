package com.example.dto;

/** 로그인 응답. Refresh Token 은 이번 범위에 없다 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

    public static TokenResponse bearer(String accessToken, long expiresInMillis) {
        return new TokenResponse(accessToken, "Bearer", expiresInMillis / 1000);
    }
}
