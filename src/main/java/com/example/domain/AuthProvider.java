package com.example.domain;

/**
 * 계정의 인증 수단.
 *
 * LOCAL 은 이메일 + 비밀번호, GOOGLE 은 구글 OAuth2 다.
 * 컬럼 기본값이 'LOCAL' 이므로 기존 계정은 전부 LOCAL 로 남는다.
 */
public enum AuthProvider {
    LOCAL,
    GOOGLE
}
