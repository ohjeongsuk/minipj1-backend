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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.JsonNode;

/**
 * 조회 전용 챗봇 통합 테스트.
 *
 * 대상 월은 2026-09 로 고정하고 asOf 를 항상 명시한다.
 * 서버 시각에 의존하면 매월 1일 0~9시에 결과가 달라진다.
 */
@Sql(scripts = "classpath:db/schema-extra.sql")
class ChatApiTest extends ApiTestSupport {

    private static final String CHAT = "/api/v1/chat";
    private static final String BUDGETS = "/api/v1/budgets";
    private static final String AS_OF = "2026-09-16";

    private String auth;
    private long foodId;
    private long salaryId;

    @BeforeEach
    void setUp() throws Exception {
        auth = signupAndLogin("chat-api@example.com");
        foodId = categoryIdByName("식비");
        salaryId = categoryIdByName("급여");
    }

    // ---------- 인증 ----------

    @Test
    @DisplayName("토큰 없이 호출하면 401 이고 응답이 ApiResponse 포맷이다")
    void 토큰_없으면_401() throws Exception {
        mockMvc.perform(post(CHAT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"이번달 얼마 썼어?","asOf":"%s"}""".formatted(AS_OF)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    // ---------- 의도 4종 ----------

    @Test
    @DisplayName("월 요약: 지출·수입·순액을 문장으로 돌려준다")
    void 월_요약() throws Exception {
        txn(foodId, "EXPENSE", "12500", "2026-09-10");
        txn(salaryId, "INCOME", "3200000", "2026-09-05");

        mockMvc.perform(ask("이번달 얼마 썼어?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.intent").value("MONTHLY_SUMMARY"))
                .andExpect(jsonPath("$.data.yearMonth").value("2026-09"))
                .andExpect(jsonPath("$.data.answer").value(
                        org.hamcrest.Matchers.containsString("12,500원")))
                .andExpect(jsonPath("$.data.answer").value(
                        org.hamcrest.Matchers.containsString("3,200,000원")));
    }

    @Test
    @DisplayName("카테고리(지출): 금액과 전체 지출 대비 비율을 돌려준다")
    void 카테고리_지출() throws Exception {
        txn(foodId, "EXPENSE", "40000", "2026-08-10");

        mockMvc.perform(ask("지난달 식비 얼마 썼어?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").value("CATEGORY_AMOUNT"))
                .andExpect(jsonPath("$.data.yearMonth").value("2026-08"))
                .andExpect(jsonPath("$.data.answer").value(
                        org.hamcrest.Matchers.containsString("40,000원")))
                .andExpect(jsonPath("$.data.answer").value(
                        org.hamcrest.Matchers.containsString("100%")));
    }

    @Test
    @DisplayName("카테고리(수입): 0원이 아니라 실제 수입 금액을 돌려준다")
    void 카테고리_수입() throws Exception {
        txn(salaryId, "INCOME", "3200000", "2026-09-05");

        mockMvc.perform(ask("이번달 급여 얼마야"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").value("CATEGORY_AMOUNT"))
                .andExpect(jsonPath("$.data.answer").value(
                        org.hamcrest.Matchers.containsString("3,200,000원")))
                .andExpect(jsonPath("$.data.answer").value(
                        org.hamcrest.Matchers.containsString("수입")));
    }

    @Test
    @DisplayName("최근 내역: 건수 상한을 지키고 지출만 거른다")
    void 최근_내역() throws Exception {
        txn(foodId, "EXPENSE", "1000", "2026-09-01");
        txn(foodId, "EXPENSE", "2000", "2026-09-02");
        txn(foodId, "EXPENSE", "3000", "2026-09-03");
        txn(salaryId, "INCOME", "3200000", "2026-09-05");

        mockMvc.perform(ask("최근 지출 2건 보여줘"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").value("RECENT_TRANSACTIONS"))
                .andExpect(jsonPath("$.data.transactions.length()").value(2))
                .andExpect(jsonPath("$.data.transactions[0].type").value("EXPENSE"))
                .andExpect(jsonPath("$.data.transactions[1].type").value("EXPENSE"));
    }

    @Test
    @DisplayName("예산: 예산·사용액·남은 금액을 돌려준다")
    void 예산() throws Exception {
        txn(foodId, "EXPENSE", "40000", "2026-09-10");
        mockMvc.perform(put(BUDGETS).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"yearMonth\":\"2026-09\",\"items\":[{\"categoryId\":%d,\"amount\":100000}]}"
                                .formatted(foodId)))
                .andExpect(status().isOk());

        mockMvc.perform(ask("식비 예산 얼마 남았어?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").value("BUDGET_STATUS"))
                .andExpect(jsonPath("$.data.answer").value(
                        org.hamcrest.Matchers.containsString("100,000원")))
                .andExpect(jsonPath("$.data.answer").value(
                        org.hamcrest.Matchers.containsString("60,000원 남았습니다")));
    }

    // ---------- 경계 ----------

    @Test
    @DisplayName("빈 메시지는 400 INVALID_INPUT 이다")
    void 빈_메시지() throws Exception {
        mockMvc.perform(ask("   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("알 수 없는 질문은 4xx 가 아니라 200 + UNKNOWN + 예시 3개다")
    void 알_수_없는_질문() throws Exception {
        mockMvc.perform(ask("안녕"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.yearMonth").doesNotExist())
                .andExpect(jsonPath("$.data.suggestions.length()").value(4));
    }

    @Test
    @DisplayName("남의 카테고리 이름은 매칭되지 않는다 (소유권)")
    void 남의_카테고리는_매칭되지_않는다() throws Exception {
        String other = signupAndLogin("chat-other@example.com");
        mockMvc.perform(post(CATEGORIES).header("Authorization", other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"요트정박비","type":"EXPENSE","color":"#123456"}"""))
                .andExpect(status().isCreated());

        // 내 카테고리에 없는 이름이므로 카테고리 조회로 잡히면 안 된다
        mockMvc.perform(ask("이번달 요트정박비 얼마 썼어?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").value("MONTHLY_SUMMARY"));
    }

    @Test
    @DisplayName("미래 달을 물어도 500 이 아니라 빈 상태 문장이고 연도를 추측하지 않는다")
    void 미래_달() throws Exception {
        mockMvc.perform(ask("12월 얼마 썼어"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yearMonth").value("2026-12"))
                .andExpect(jsonPath("$.data.answer").value(
                        org.hamcrest.Matchers.containsString("기록이 없어요")));
    }


    // ---------- 2단계 의도 ----------

    @Test
    @DisplayName("예측: 직전 3개월 기록이 없으면 예측하지 않는다고 답한다")
    void 예측_데이터_부족() throws Exception {
        txn(foodId, "EXPENSE", "10000", "2026-09-10");

        mockMvc.perform(ask("이 속도면 얼마 쓸까?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").value("FORECAST"))
                .andExpect(jsonPath("$.data.answer").value(
                        org.hamcrest.Matchers.containsString("데이터가 조금 더 필요해요")));
    }

    @Test
    @DisplayName("예측: 기준선이 있으면 예상 지출을 문장으로 준다")
    void 예측_정상() throws Exception {
        // 직전 3개월(6~8월)에 기록을 심는다
        txn(foodId, "EXPENSE", "300000", "2026-06-15");
        txn(foodId, "EXPENSE", "300000", "2026-07-15");
        txn(foodId, "EXPENSE", "300000", "2026-08-15");
        txn(foodId, "EXPENSE", "100000", "2026-09-10");

        mockMvc.perform(ask("이번달 예상 지출 얼마야"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").value("FORECAST"))
                .andExpect(jsonPath("$.data.yearMonth").value("2026-09"))
                .andExpect(jsonPath("$.data.answer").value(
                        org.hamcrest.Matchers.containsString("이 속도면")));
    }

    @Test
    @DisplayName("고정지출: 없으면 조건을 알려준다")
    void 고정지출_없음() throws Exception {
        mockMvc.perform(ask("고정지출 뭐 있어?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").value("RECURRING"))
                // 대상 월이 없는 계산이라 특정 달의 값처럼 보이면 안 된다
                .andExpect(jsonPath("$.data.yearMonth").doesNotExist())
                .andExpect(jsonPath("$.data.answer").value(
                        org.hamcrest.Matchers.containsString("아직 없어요")));
    }

    @Test
    @DisplayName("고정지출: 같은 상호가 석 달 이어지면 찾아낸다")
    void 고정지출_감지() throws Exception {
        txn(foodId, "EXPENSE", "17000", "2026-06-05", "넷플릭스");
        txn(foodId, "EXPENSE", "17000", "2026-07-05", "넷플릭스");
        txn(foodId, "EXPENSE", "17000", "2026-08-05", "넷플릭스");

        mockMvc.perform(ask("고정지출 뭐 있어?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").value("RECURRING"))
                .andExpect(jsonPath("$.data.answer").value(
                        org.hamcrest.Matchers.containsString("넷플릭스")))
                .andExpect(jsonPath("$.data.answer").value(
                        org.hamcrest.Matchers.containsString("17,000원")));
    }

    @Test
    @DisplayName("일별: 특정 날짜의 금액을 준다")
    void 일별_정상() throws Exception {
        txn(foodId, "EXPENSE", "32000", "2026-09-15");

        mockMvc.perform(ask("15일 얼마 썼어?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").value("DAILY_AMOUNT"))
                .andExpect(jsonPath("$.data.answer").value(
                        org.hamcrest.Matchers.containsString("9월 15일")))
                .andExpect(jsonPath("$.data.answer").value(
                        org.hamcrest.Matchers.containsString("32,000원")));
    }

    @Test
    @DisplayName("일별: 기록이 없는 날은 없다고 답한다")
    void 일별_빈날() throws Exception {
        mockMvc.perform(ask("15일 얼마 썼어?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value(
                        org.hamcrest.Matchers.containsString("기록이 없어요")));
    }

    @Test
    @DisplayName("일별 + 카테고리: 0원이라 하지 않고 한계를 밝힌 뒤 그날 전체를 준다")
    void 일별_카테고리_한계() throws Exception {
        txn(foodId, "EXPENSE", "32000", "2026-09-15");

        mockMvc.perform(ask("15일 식비 얼마 썼어?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").value("DAILY_AMOUNT"))
                .andExpect(jsonPath("$.data.answer").value(
                        org.hamcrest.Matchers.containsString("카테고리를 나눠서 보여드릴 수 없어요")))
                .andExpect(jsonPath("$.data.answer").value(
                        org.hamcrest.Matchers.containsString("32,000원")));
    }

    // ---------- 헬퍼 ----------

    private MockHttpServletRequestBuilder ask(String message) {
        return post(CHAT).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"%s\",\"asOf\":\"%s\"}".formatted(message, AS_OF));
    }

    private void txn(long categoryId, String type, String amount, String date) throws Exception {
        txn(categoryId, type, amount, date, null);
    }

    private void txn(long categoryId, String type, String amount, String date, String merchant)
            throws Exception {
        String merchantJson = merchant == null ? "null" : "\"" + merchant + "\"";
        mockMvc.perform(post(TRANSACTIONS).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":%d,\"type\":\"%s\",\"amount\":%s,\"txnDate\":\"%s\",\"merchant\":%s}"
                                .formatted(categoryId, type, amount, date, merchantJson)))
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
