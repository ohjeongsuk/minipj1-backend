package com.example.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.domain.AuthProvider;
import com.example.domain.CategoryRepository;
import com.example.domain.User;
import com.example.domain.UserRepository;
import com.example.exception.BusinessException;
import com.example.exception.ErrorCode;
import com.example.service.AuthService;

/**
 * 구글 로그인 (AUTH-09).
 *
 * 구글 서버와의 왕복은 여기서 다루지 않는다 — 그 부분은 Spring Security 가 처리하고
 * 실제 자격증명이 필요하다. 우리가 책임지는 것은 프로필을 받은 뒤의 분기다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GoogleOAuthIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("구글로 처음 로그인하면 가입되고 기본 카테고리 9개가 함께 생긴다")
    void 구글_신규가입시_기본카테고리가_생성된다() {
        User user = authService.loginOrRegisterGoogle(
                "new.google@moneylog.test", "구글사용자", "google-sub-0001");

        assertThat(user.getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(user.getProviderId()).isEqualTo("google-sub-0001");
        // 구글 계정에는 비밀번호가 없다
        assertThat(user.getPassword()).isNull();
        assertThat(user.canLoginWithPassword()).isFalse();
        assertThat(categoryRepository.findByUserIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user.getId()))
                .hasSize(9);
    }

    @Test
    @DisplayName("같은 sub 로 다시 로그인하면 계정이 새로 만들어지지 않는다")
    void 재로그인시_같은_계정을_돌려준다() {
        User first = authService.loginOrRegisterGoogle(
                "repeat@moneylog.test", "반복", "google-sub-0002");
        User second = authService.loginOrRegisterGoogle(
                "repeat@moneylog.test", "반복", "google-sub-0002");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(userRepository.findByEmailAndDeletedAtIsNull("repeat@moneylog.test")).isPresent();
    }

    @Test
    @DisplayName("같은 이메일의 로컬 계정이 있으면 구글 로그인을 거부한다")
    void 이메일이_겹치면_EMAIL_CONFLICT() throws Exception {
        // 먼저 일반 가입으로 계정을 만든다
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"conflict@moneylog.test","password":"password1","nickname":"로컬"}
                                """))
                .andExpect(status().isCreated());

        // 자동 연동하지 않는다. 구글 이메일 소유만으로 기존 비밀번호 계정을 차지하면 안 된다
        assertThatThrownBy(() -> authService.loginOrRegisterGoogle(
                "conflict@moneylog.test", "구글", "google-sub-0003"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_CONFLICT);
    }

    @Test
    @DisplayName("구글 계정은 비밀번호 로그인을 할 수 없고, 일반 실패와 같은 401 문구를 쓴다")
    void 구글계정은_비밀번호로그인_불가() throws Exception {
        authService.loginOrRegisterGoogle("pwless@moneylog.test", "구글", "google-sub-0004");

        // password 가 NULL 이라 matches(raw, null) 을 부르면 500 이 난다. 401 이어야 한다
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"pwless@moneylog.test","password":"password1"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").value("이메일 또는 비밀번호가 올바르지 않습니다."));
    }
}
