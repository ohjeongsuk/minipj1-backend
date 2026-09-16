package com.example.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import com.example.domain.User;
import com.example.exception.BusinessException;

/**
 * 구글에서 받은 프로필로 우리 사용자를 찾거나 만든다 (AUTH-09).
 *
 * ⚠️ OidcUserService 를 상속한다. DefaultOAuth2UserService 가 아니다.
 *    scope 에 openid 가 들어 있으면 Spring Security 는 OIDC 흐름을 타고
 *    userInfoEndpoint().userService(...) 로 등록한 OAuth2UserService 를 아예 부르지 않는다.
 *    그러면 우리 코드가 조용히 건너뛰어지고, 인증은 성공한 채로
 *    SuccessHandler 에서 속성이 없어 터진다. oidcUserService(...) 로 등록해야 한다.
 *
 * ⚠️ 충돌 판정을 SuccessHandler 가 아니라 여기서 한다.
 *    loadUser 에서 OAuth2AuthenticationException 을 던져야 failureHandler 로 간다.
 *    SuccessHandler 안에서 던지면 그 경로를 타지 못하고 500 이 그대로 나간다.
 *
 * ⚠️ 사용자 식별자는 sub 다. 이메일은 구글에서 바뀔 수 있다.
 */
@Service
public class CustomOidcUserService extends OidcUserService {

    /** SuccessHandler 가 우리 사용자 id 를 꺼낼 때 쓰는 키 */
    public static final String USER_ID_ATTRIBUTE = "moneylogUserId";

    private final AuthService authService;

    public CustomOidcUserService(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);

        String providerId = oidcUser.getSubject();
        String email = oidcUser.getEmail();
        String name = oidcUser.getFullName();

        if (providerId == null || email == null) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_profile"), "구글 프로필에 sub 또는 email 이 없습니다.");
        }

        User user;
        try {
            // 닉네임이 비어 있으면 이메일 앞부분을 쓴다. nickname 은 NOT NULL 이다
            String nickname = (name == null || name.isBlank()) ? email.split("@")[0] : name;
            user = authService.loginOrRegisterGoogle(email, nickname, providerId);
        } catch (BusinessException e) {
            // 오류 코드를 그대로 실어 보내면 FailureHandler 가 프론트로 전달할 수 있다
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(e.getErrorCode().name()), e.getMessage(), e);
        }

        /*
         * DefaultOidcUser 의 getAttributes() 는 idToken 클레임과 userInfo 클레임을 합친다.
         * 우리 사용자 id 를 userInfo 쪽에 얹어 SuccessHandler 가 꺼내 쓰게 한다.
         */
        Map<String, Object> claims = new HashMap<>(oidcUser.getClaims());
        claims.put(USER_ID_ATTRIBUTE, user.getId());

        return new DefaultOidcUser(
                oidcUser.getAuthorities(),
                oidcUser.getIdToken(),
                new OidcUserInfo(claims),
                "sub");
    }
}
