package com.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 인코더만 담는다.
 *
 * ⚠️ SecurityConfig 안에 두지 않는다.
 *    AuthService 가 PasswordEncoder 를 주입받는데, SecurityConfig 가 구글 로그인을 위해
 *    CustomOidcUserService → AuthService 를 의존하게 되면서
 *    SecurityConfig → AuthService → PasswordEncoder → SecurityConfig 순환이 생겼다.
 *    필터 체인 설정과 비밀번호 해싱은 원래 다른 관심사이므로 분리하는 편이 맞다.
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
