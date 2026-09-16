package com.example.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

/**
 * 카테고리·거래 API 통합 테스트의 공통 설정과 헬퍼.
 * 가입 → 로그인 → Bearer 토큰까지를 한 줄로 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class ApiTestSupport {

    protected static final String CATEGORIES = "/api/v1/categories";
    protected static final String TRANSACTIONS = "/api/v1/transactions";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /** 가입 후 로그인해 "Bearer xxx" 형태의 헤더 값을 돌려준다 */
    protected String signupAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password1","nickname":"테스터"}"""
                                .formatted(email)))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password1"}""".formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        return "Bearer " + objectMapper.readTree(body).get("data").get("accessToken").asString();
    }

    /** 응답 본문에서 data 하위 경로의 값을 꺼낸다 */
    protected String dataText(String body, String... path) {
        var node = objectMapper.readTree(body).get("data");
        for (String key : path) {
            node = node.get(key);
        }
        return node.asString();
    }

    protected long dataLong(String body, String... path) {
        var node = objectMapper.readTree(body).get("data");
        for (String key : path) {
            node = node.get(key);
        }
        return node.asLong();
    }
}
