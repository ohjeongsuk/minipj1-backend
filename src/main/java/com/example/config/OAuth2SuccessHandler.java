package com.example.config;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.example.service.CustomOidcUserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 구글 인증이 끝나면 우리 JWT 를 발급해 프론트로 돌려보낸다 (AUTH-09).
 *
 * ⚠️ 토큰을 쿼리스트링이 아니라 URL 프래그먼트(#)로 넘긴다.
 *    프래그먼트는 서버로 전송되지 않아 액세스 로그와 Referer 헤더에 JWT 가 남지 않는다.
 *    프론트는 값을 읽는 즉시 history.replaceState 로 해시를 지운다.
 */
@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider tokenProvider;
    private final String redirectUri;

    public OAuth2SuccessHandler(JwtTokenProvider tokenProvider,
                                @Value("${app.oauth2.redirect-uri}") String redirectUri) {
        this.tokenProvider = tokenProvider;
        this.redirectUri = redirectUri;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User principal = (OAuth2User) authentication.getPrincipal();
        Object rawUserId = principal.getAttributes().get(CustomOidcUserService.USER_ID_ATTRIBUTE);
        if (rawUserId == null) {
            /*
             * 우리 UserService 가 호출되지 않았다는 뜻이다. 대표적인 원인은
             * OIDC 흐름인데 oauth2UserService 쪽에만 등록한 경우다.
             * 숫자 파싱에서 NumberFormatException 으로 터지면 원인이 보이지 않으므로 여기서 막는다.
             */
            throw new IllegalStateException(
                    "OAuth2 사용자 서비스가 호출되지 않았습니다. oidcUserService 등록을 확인하세요.");
        }
        Long userId = Long.valueOf(String.valueOf(rawUserId));
        String email = String.valueOf(principal.getAttributes().get("email"));

        String token = tokenProvider.createToken(userId, email);
        response.sendRedirect(redirectUri + "#token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8));
    }
}
