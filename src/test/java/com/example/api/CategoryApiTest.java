package com.example.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;

/**
 * CAT-01~05.
 * @Sql 로 부분 유니크 인덱스를 적용한다. create-drop 이라 테스트 DB 에는 없기 때문이다.
 */
@Sql(scripts = "classpath:db/schema-extra.sql")
class CategoryApiTest extends ApiTestSupport {

    private String auth;

    @BeforeEach
    void setUp() throws Exception {
        auth = signupAndLogin("cat-api@example.com");
    }

    @Test
    @DisplayName("가입 직후 목록이 기본 카테고리 9개를 반환한다 (Phase 3 DoD 2번 완결)")
    void 기본_카테고리_9개를_반환한다() throws Exception {
        mockMvc.perform(get(CATEGORIES).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(9))
                .andExpect(jsonPath("$.data[0].name").value("식비"))
                .andExpect(jsonPath("$.data[0].color").value("#EF4444"))
                .andExpect(jsonPath("$.data[0].deleted").value(false));
    }

    @Test
    @DisplayName("?type= 으로 구분을 필터할 수 있다")
    void 구분으로_필터한다() throws Exception {
        mockMvc.perform(get(CATEGORIES).param("type", "INCOME").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(get(CATEGORIES).param("type", "EXPENSE").header("Authorization", auth))
                .andExpect(jsonPath("$.data.length()").value(7));
    }

    @Test
    @DisplayName("카테고리를 생성하면 201 을 반환한다 (CAT-01)")
    void 카테고리를_생성한다() throws Exception {
        mockMvc.perform(post(CATEGORIES).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"반려동물","type":"EXPENSE","color":"#8B5CF6","sortOrder":9}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("반려동물"))
                .andExpect(jsonPath("$.data.type").value("EXPENSE"));
    }

    @Test
    @DisplayName("색상이 #RRGGBB 형식이 아니면 400 이다 (CSS 값 주입 경로 차단)")
    void 잘못된_색상은_400() throws Exception {
        mockMvc.perform(post(CATEGORIES).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"테스트","type":"EXPENSE","color":"red; background:url(x)"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("같은 구분에서 이름이 중복되면 409 CATEGORY_DUPLICATED (CAT-04)")
    void 중복_이름은_409() throws Exception {
        mockMvc.perform(post(CATEGORIES).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"식비","type":"EXPENSE","color":"#EF4444"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_DUPLICATED"));
    }

    @Test
    @DisplayName("구분이 다르면 같은 이름을 쓸 수 있다")
    void 구분이_다르면_같은_이름이_허용된다() throws Exception {
        // "기타"는 EXPENSE·INCOME 양쪽에 이미 있다. 여기서는 새 이름으로 확인한다
        mockMvc.perform(post(CATEGORIES).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"식비","type":"INCOME","color":"#10B981"}"""))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("삭제한 이름은 다시 쓸 수 있다 (CAT-04 부분 유니크)")
    void 삭제한_이름을_재사용한다() throws Exception {
        long id = firstCategoryId();

        mockMvc.perform(delete(CATEGORIES + "/" + id).header("Authorization", auth))
                .andExpect(status().isOk());

        mockMvc.perform(post(CATEGORIES).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"식비","type":"EXPENSE","color":"#F97316"}"""))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("이름·색·순서를 수정할 수 있다 (CAT-02)")
    void 카테고리를_수정한다() throws Exception {
        long id = firstCategoryId();

        mockMvc.perform(put(CATEGORIES + "/" + id).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"먹는거","color":"#F97316","sortOrder":3}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("먹는거"))
                .andExpect(jsonPath("$.data.color").value("#F97316"))
                .andExpect(jsonPath("$.data.sortOrder").value(3))
                // type 은 요청에 없었고 바뀌지도 않는다
                .andExpect(jsonPath("$.data.type").value("EXPENSE"));
    }

    @Test
    @DisplayName("요청에 type 을 넣어도 무시된다 (DTO 에 필드가 없어 API 스펙상 변경 불가)")
    void type_은_수정되지_않는다() throws Exception {
        long id = firstCategoryId();

        mockMvc.perform(put(CATEGORIES + "/" + id).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"식비","color":"#EF4444","sortOrder":0,"type":"INCOME"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("EXPENSE"));
    }

    @Test
    @DisplayName("삭제하면 목록에서 빠진다 (CAT-03)")
    void 삭제하면_목록에서_빠진다() throws Exception {
        long id = firstCategoryId();

        mockMvc.perform(delete(CATEGORIES + "/" + id).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get(CATEGORIES).header("Authorization", auth))
                .andExpect(jsonPath("$.data.length()").value(8));
    }

    @Test
    @DisplayName("타 사용자의 카테고리는 조회·수정·삭제할 수 없다 — 404 (CAT-05)")
    void 남의_카테고리는_404() throws Exception {
        long id = firstCategoryId();
        String other = signupAndLogin("cat-other@example.com");

        mockMvc.perform(put(CATEGORIES + "/" + id).header("Authorization", other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"탈취","color":"#000000","sortOrder":0}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_NOT_FOUND"));

        mockMvc.perform(delete(CATEGORIES + "/" + id).header("Authorization", other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_NOT_FOUND"));
    }

    private long firstCategoryId() throws Exception {
        String body = mockMvc.perform(get(CATEGORIES).header("Authorization", auth))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).get("data").get(0).get("id").asLong();
    }
}
