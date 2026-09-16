package com.example.api;

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

import tools.jackson.databind.JsonNode;

/**
 * CLAUDE.md 12장 Phase 6 테스트 20~21번 + Phase 6 DoD 추가분.
 */
@Sql(scripts = "classpath:db/schema-extra.sql")
class BudgetApiTest extends ApiTestSupport {

    private static final String BUDGETS = "/api/v1/budgets";
    private static final String MONTHLY = "/api/v1/stats/monthly";

    private String auth;
    private long foodId;
    private long transportId;

    @BeforeEach
    void setUp() throws Exception {
        auth = signupAndLogin("budget-api@example.com");
        foodId = categoryIdByName("식비");
        transportId = categoryIdByName("교통");
    }

    @Test
    @DisplayName("GET 은 미설정 카테고리도 포함해 지출 카테고리 전체를 반환한다")
    void 목록은_전체_카테고리를_반환한다() throws Exception {
        mockMvc.perform(get(BUDGETS).header("Authorization", auth).param("yearMonth", "2026-09"))
                .andExpect(status().isOk())
                // 기본 지출 카테고리 7개
                .andExpect(jsonPath("$.data.length()").value(7))
                .andExpect(jsonPath("$.data[0].name").value("식비"))
                // 미설정은 amount 가 null 이다 (0 과 구분한다)
                .andExpect(jsonPath("$.data[0].amount").doesNotExist());
    }

    @Test
    @DisplayName("upsert 가 새 행을 만들고 다시 호출하면 기존 행을 갱신한다")
    void upsert_생성과_갱신() throws Exception {
        upsert("2026-09", """
                [{"categoryId":%d,"amount":600000}]""".formatted(foodId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("식비"))
                .andExpect(jsonPath("$.data[0].amount").value(600000.00));

        upsert("2026-09", """
                [{"categoryId":%d,"amount":700000}]""".formatted(foodId))
                .andExpect(jsonPath("$.data[0].amount").value(700000.00));

        // 행이 늘어나지 않았는지 (갱신이지 추가가 아니다)
        mockMvc.perform(get(BUDGETS).header("Authorization", auth).param("yearMonth", "2026-09"))
                .andExpect(jsonPath("$.data[0].amount").value(700000.00))
                .andExpect(jsonPath("$.data[1].amount").doesNotExist());
    }

    @Test
    @DisplayName("amount 가 0 이면 행을 제거한다 (미설정 상태로 되돌림)")
    void 금액_0은_행을_제거한다() throws Exception {
        upsert("2026-09", """
                [{"categoryId":%d,"amount":600000}]""".formatted(foodId))
                .andExpect(jsonPath("$.data[0].amount").value(600000.00));

        upsert("2026-09", """
                [{"categoryId":%d,"amount":0}]""".formatted(foodId))
                .andExpect(jsonPath("$.data[0].amount").doesNotExist());
    }

    @Test
    @DisplayName("amount 가 null 이어도 행을 제거한다")
    void 금액_null도_행을_제거한다() throws Exception {
        upsert("2026-09", """
                [{"categoryId":%d,"amount":600000}]""".formatted(foodId));

        upsert("2026-09", """
                [{"categoryId":%d,"amount":null}]""".formatted(foodId))
                .andExpect(jsonPath("$.data[0].amount").doesNotExist());
    }

    @Test
    @DisplayName("여러 카테고리를 한 번에 저장한다")
    void 일괄_저장() throws Exception {
        upsert("2026-09", """
                [{"categoryId":%d,"amount":600000},{"categoryId":%d,"amount":150000}]"""
                .formatted(foodId, transportId))
                .andExpect(jsonPath("$.data[0].amount").value(600000.00))
                .andExpect(jsonPath("$.data[1].amount").value(150000.00));
    }

    @Test
    @DisplayName("예산은 월별로 분리된다")
    void 월별로_분리된다() throws Exception {
        upsert("2026-09", """
                [{"categoryId":%d,"amount":600000}]""".formatted(foodId));

        mockMvc.perform(get(BUDGETS).header("Authorization", auth).param("yearMonth", "2026-10"))
                .andExpect(jsonPath("$.data[0].amount").doesNotExist());
    }

    @Test
    @DisplayName("저장한 예산이 stats/monthly 의 budgets 에 즉시 반영된다")
    void 대시보드에_즉시_반영된다() throws Exception {
        txn(foodId, "412000", "2026-09-05");
        upsert("2026-09", """
                [{"categoryId":%d,"amount":600000}]""".formatted(foodId));

        mockMvc.perform(get(MONTHLY).header("Authorization", auth)
                        .param("yearMonth", "2026-09").param("asOf", "2026-09-16"))
                .andExpect(jsonPath("$.data.budgets.length()").value(1))
                .andExpect(jsonPath("$.data.budgets[0].name").value("식비"))
                .andExpect(jsonPath("$.data.budgets[0].budget").value(600000.00))
                .andExpect(jsonPath("$.data.budgets[0].spent").value(412000.00))
                // 412,000 / 600,000 = 0.6867
                .andExpect(jsonPath("$.data.budgets[0].usageRatio").value(0.6867))
                .andExpect(jsonPath("$.data.budgets[0].exceeded").value(false));
    }

    @Test
    @DisplayName("지출이 예산을 넘으면 exceeded 가 true 다")
    void 예산_초과() throws Exception {
        txn(foodId, "700000", "2026-09-05");
        upsert("2026-09", """
                [{"categoryId":%d,"amount":600000}]""".formatted(foodId));

        mockMvc.perform(get(MONTHLY).header("Authorization", auth)
                        .param("yearMonth", "2026-09").param("asOf", "2026-09-16"))
                .andExpect(jsonPath("$.data.budgets[0].exceeded").value(true));
    }

    @Test
    @DisplayName("예산이 0이면 usageRatio 가 Infinity·NaN 이 아니라 0 이다")
    void 예산_0의_소진율() throws Exception {
        txn(foodId, "412000", "2026-09-05");
        // amount 0 은 행을 제거하므로 budgets 배열 자체가 비어 Infinity 가 실릴 경로가 없다
        upsert("2026-09", """
                [{"categoryId":%d,"amount":0}]""".formatted(foodId));

        String body = mockMvc.perform(get(MONTHLY).header("Authorization", auth)
                        .param("yearMonth", "2026-09").param("asOf", "2026-09-16"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // JSON 에 Infinity/NaN 리터럴이 실리면 표준 파서가 거부한다. 파싱이 되는 것 자체가 검증이다
        assertNoNonFiniteNumber(body);
        org.assertj.core.api.Assertions
                .assertThat(objectMapper.readTree(body).get("data").get("budgets")).isEmpty();
    }

    @Test
    @DisplayName("타 사용자의 카테고리로 예산을 저장하려 하면 404")
    void 남의_카테고리는_404() throws Exception {
        String other = signupAndLogin("budget-other@example.com");

        mockMvc.perform(put(BUDGETS).header("Authorization", other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"yearMonth":"2026-09","items":[{"categoryId":%d,"amount":600000}]}"""
                                .formatted(foodId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    @DisplayName("yearMonth 형식이 틀리면 400")
    void 잘못된_yearMonth() throws Exception {
        mockMvc.perform(put(BUDGETS).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"yearMonth":"2026-13","items":[]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("예산이 음수면 400")
    void 음수_예산은_400() throws Exception {
        mockMvc.perform(put(BUDGETS).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"yearMonth":"2026-09","items":[{"categoryId":%d,"amount":-1}]}"""
                                .formatted(foodId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    // ---------- 헬퍼 ----------

    private void assertNoNonFiniteNumber(String body) {
        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("Infinity").doesNotContain("NaN");
    }

    private org.springframework.test.web.servlet.ResultActions upsert(String yearMonth, String itemsJson)
            throws Exception {
        return mockMvc.perform(put(BUDGETS).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"yearMonth":"%s","items":%s}""".formatted(yearMonth, itemsJson)));
    }

    private void txn(long categoryId, String amount, String date) throws Exception {
        mockMvc.perform(post(TRANSACTIONS).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":%d,\"type\":\"EXPENSE\",\"amount\":%s,\"txnDate\":\"%s\"}"
                                .formatted(categoryId, amount, date)))
                .andExpect(status().isCreated());
    }

    private long categoryIdByName(String name) throws Exception {
        String body = mockMvc.perform(get(CATEGORIES).header("Authorization", auth))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        for (JsonNode node : objectMapper.readTree(body).get("data")) {
            if (name.equals(node.get("name").asString())) {
                return node.get("id").asLong();
            }
        }
        throw new IllegalStateException("카테고리를 찾을 수 없습니다: " + name);
    }
}
