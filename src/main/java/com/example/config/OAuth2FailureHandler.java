package com.example.config;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 구글 인증 실패를 프론트로 전달한다 (AUTH-09).
 *
 * 가장 흔한 경우는 EMAIL_CONFLICT 다 — 같은 이메일의 로컬 계정이 이미 있는 경우.
 * 여기서도 프래그먼트를 쓴다. 성공 경로와 형태를 맞춰 프론트가 한 곳에서 처리한다.
 *
 * ⚠️ 예외 메시지를 그대로 실어 보내지 않는다. 내부 사정이 URL 에 드러난다.
 *    프론트가 아는 코드만 넘기고 문구는 프론트의 매핑 표를 따른다.
 */
@Component
public class OAuth2FailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2FailureHandler.class);
    private static final String DEFAULT_CODE = "OAUTH2_FAILED";

    private final String redirectUri;

    public OAuth2FailureHandler(@Value("${app.oauth2.redirect-uri}") String redirectUri) {
        this.redirectUri = redirectUri;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {

        // 원인은 서버 로그로만 남긴다. URL 에 내부 사정을 싣지 않는다
        log.warn("구글 로그인 실패", exception);

        String code = DEFAULT_CODE;
        if (exception instanceof OAuth2AuthenticationException oauthException) {
            String errorCode = oauthException.getError().getErrorCode();
            if (errorCode != null && !errorCode.isBlank()) {
                code = errorCode;
            }
        }

        response.sendRedirect(redirectUri + "#error="
                + URLEncoder.encode(code, StandardCharsets.UTF_8));
    }
}
