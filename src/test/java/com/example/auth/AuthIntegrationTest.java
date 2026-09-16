package com.example.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.domain.Category;
import com.example.domain.CategoryRepository;
import com.example.domain.TransactionType;
import com.example.domain.UserRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * CLAUDE.md 12장 Phase 3 통합 테스트 1~5번 + Phase 3 DoD 추가분.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthIntegrationTest {

    private static final String SIGNUP = "/api/v1/auth/signup";
    private static final String LOGIN = "/api/v1/auth/login";
    private static final String ME = "/api/v1/auth/me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    // ---------- 1. 회원가입 ----------

    @Test
    @DisplayName("회원가입에 성공하면 201 과 내 정보를 반환한다 (CSRF 비활성화 확인 — 403 이 아니다)")
    void 회원가입_성공() throws Exception {
        mockMvc.perform(post(SIGNUP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("new@example.com", "password1", "테스터")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("new@example.com"))
                .andExpect(jsonPath("$.data.nickname").value("테스터"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    @DisplayName("비밀번호는 응답에 포함되지 않는다")
    void 응답에_비밀번호가_없다() throws Exception {
        String body = mockMvc.perform(post(SIGNUP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("nopw@example.com", "password1", "테스터")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).doesNotContain("password");
    }

    @Test
    @DisplayName("이미 가입된 이메일로 가입하면 409 EMAIL_DUPLICATED")
    void 중복_이메일은_409() throws Exception {
        signup("dup@example.com", "password1", "먼저");

        mockMvc.perform(post(SIGNUP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("dup@example.com", "password1", "나중")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("EMAIL_DUPLICATED"));
    }

    // ---------- 2. 기본 카테고리 ----------

    @Test
    @DisplayName("가입하면 기본 카테고리 9개가 같은 트랜잭션에서 생성된다 (EXPENSE 7 + INCOME 2)")
    void 기본_카테고리_9개가_생성된다() throws Exception {
        signup("cat@example.com", "password1", "테스터");

        Long userId = userRepository.findByEmailAndDeletedAtIsNull("cat@example.com")
                .orElseThrow().getId();
        List<Category> categories =
                categoryRepository.findByUserIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(userId);

        assertThat(categories).hasSize(9);
        assertThat(categories).filteredOn(c -> c.getType() == TransactionType.EXPENSE).hasSize(7);
        assertThat(categories).filteredOn(c -> c.getType() == TransactionType.INCOME).hasSize(2);
        assertThat(categories).extracting(Category::getName)
                .containsExactly("식비", "교통", "주거/통신", "생활용품", "문화/여가",
                        "의료/건강", "기타", "급여", "기타수입");
        // 색은 #RRGGBB 형식이어야 한다. 검증 없이 인라인 스타일에 들어가면 CSS 주입 경로가 된다
        assertThat(categories).allSatisfy(c -> assertThat(c.getColor()).matches("^#[0-9A-F]{6}$"));
    }

    // ---------- 3. 로그인 ----------

    @Test
    @DisplayName("로그인에 성공하면 JWT 를 반환하고 헤더의 alg 가 HS256 이다")
    void 로그인_성공시_HS256_토큰() throws Exception {
        signup("login@example.com", "password1", "테스터");
        String token = login("login@example.com", "password1");

        assertThat(token).isNotBlank();

        // ⚠️ 인자 없는 signWith(key) 를 쓰면 jjwt 가 키 길이로 알고리즘을 추론한다.
        // 현재 JWT_SECRET 은 48바이트를 넘어 HS384 가 되는데, 어디서도 오류가 나지 않는다.
        // 헤더를 직접 디코드해야만 드러나므로 테스트로 고정한다.
        JsonNode header = objectMapper.readTree(
                Base64.getUrlDecoder().decode(token.split("\\.")[0]));
        assertThat(header.get("alg").asString()).isEqualTo("HS256");

        // sub 에는 이메일이 아니라 user.id 가 들어간다
        JsonNode payload = objectMapper.readTree(
                Base64.getUrlDecoder().decode(token.split("\\.")[1]));
        Long userId = userRepository.findByEmailAndDeletedAtIsNull("login@example.com")
                .orElseThrow().getId();
        assertThat(payload.get("sub").asString()).isEqualTo(String.valueOf(userId));
        assertThat(payload.get("email").asString()).isEqualTo("login@example.com");
    }

    @Test
    @DisplayName("비밀번호가 틀리면 401 이고, 미가입 이메일과 응답 메시지가 동일하다")
    void 로그인_실패는_구분되지_않는다() throws Exception {
        signup("exists@example.com", "password1", "테스터");

        String wrongPassword = mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("exists@example.com", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String unknownEmail = mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("nobody@example.com", "password1")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // 계정 존재 여부가 응답으로 드러나면 안 된다
        assertThat(wrongPassword).isEqualTo(unknownEmail);
    }

    // ---------- 4. 인증 ----------

    @Nested
    @DisplayName("보호된 엔드포인트")
    class ProtectedEndpoint {

        @Test
        @DisplayName("토큰 없이 호출하면 401 이고 응답이 ApiResponse 포맷이다")
        void 토큰_없으면_401() throws Exception {
            mockMvc.perform(get(ME))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data").doesNotExist())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                    .andExpect(jsonPath("$.error.message").exists());
        }

        @Test
        @DisplayName("깨진 토큰으로 호출해도 500 이 아니라 401 이다")
        void 깨진_토큰도_401() throws Exception {
            mockMvc.perform(get(ME).header("Authorization", "Bearer not-a-jwt"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("유효한 토큰으로 호출하면 email 과 nickname 을 반환한다")
        void 토큰이_있으면_내_정보를_반환한다() throws Exception {
            signup("me@example.com", "password1", "홍길동");
            String token = login("me@example.com", "password1");

            mockMvc.perform(get(ME).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.email").value("me@example.com"))
                    .andExpect(jsonPath("$.data.nickname").value("홍길동"));
        }
    }

    // ---------- 5. 비밀번호 바이트 검증 ----------

    @Test
    @DisplayName("한글 25자(75바이트) 비밀번호는 500 이 아니라 400 INVALID_INPUT 이다")
    void 한글_25자_비밀번호는_400() throws Exception {
        String password = "가".repeat(25);
        assertThat(password.length()).isEqualTo(25);
        assertThat(password.getBytes(StandardCharsets.UTF_8)).hasSize(75);

        mockMvc.perform(post(SIGNUP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("long@example.com", password, "테스터")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                // 어느 필드가 문제인지 알려줘야 한다
                .andExpect(jsonPath("$.error.message").value(Matchers.containsString("password")));
    }

    @Test
    @DisplayName("한글 24자(72바이트) 비밀번호는 경계값이라 통과한다")
    void 한글_24자_비밀번호는_통과한다() throws Exception {
        String password = "가".repeat(24);
        assertThat(password.getBytes(StandardCharsets.UTF_8)).hasSize(72);

        mockMvc.perform(post(SIGNUP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("edge@example.com", password, "테스터")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("JSON 문법이 깨진 본문은 500 이 아니라 400 INVALID_INPUT 이다")
    void 깨진_JSON_본문은_400() throws Exception {
        // 클라이언트가 보낸 요청의 문제이므로 서버 오류가 아니다.
        // GlobalExceptionHandler 에 매핑이 없으면 catch-all 이 삼켜 INTERNAL_ERROR 로 나간다.
        mockMvc.perform(post(SIGNUP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("필드 타입이 맞지 않는 본문도 400 INVALID_INPUT 이다")
    void 타입_불일치_본문은_400() throws Exception {
        mockMvc.perform(post(SIGNUP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":123,\"password\":true,\"nickname\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("비밀번호가 5자면 400 INVALID_INPUT")
    void 짧은_비밀번호는_400() throws Exception {
        mockMvc.perform(post(SIGNUP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody("short@example.com", "12345", "테스터")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    // ---------- 헬퍼 ----------

    private void signup(String email, String password, String nickname) throws Exception {
        mockMvc.perform(post(SIGNUP)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody(email, password, nickname)))
                .andExpect(status().isCreated());
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).get("data").get("accessToken").asString();
    }

    private String signupBody(String email, String password, String nickname) {
        return "{\"email\":\"%s\",\"password\":\"%s\",\"nickname\":\"%s\"}"
                .formatted(email, password, nickname);
    }

    private String loginBody(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }
}
