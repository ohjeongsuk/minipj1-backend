package com.example.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;

import tools.jackson.databind.JsonNode;

/**
 * CLAUDE.md 12장 Phase 5 통합 테스트 14~19번 + Phase 5 DoD 추가분.
 *
 * 대상 월은 2026-09(30일), 기준선 구간은 2026-06~08(92일)로 고정한다.
 * 서버 시각과 무관해야 하므로 모든 날짜를 파라미터로 명시한다.
 */
@Sql(scripts = "classpath:db/schema-extra.sql")
class StatsApiTest extends ApiTestSupport {

    private static final String MONTHLY = "/api/v1/stats/monthly";
    private static final String RECURRING = "/api/v1/stats/recurring";

    private String auth;
    private long foodId;      // 식비 (EXPENSE)
    private long transportId; // 교통 (EXPENSE)
    private long salaryId;    // 급여 (INCOME)

    @BeforeEach
    void setUp() throws Exception {
        auth = signupAndLogin("stats-api@example.com");
        foodId = categoryIdByName("식비");
        transportId = categoryIdByName("교통");
        salaryId = categoryIdByName("급여");
    }

    // ---------- 14. 거래 없는 달 ----------

    @Test
    @DisplayName("거래가 없는 달을 조회해도 500이 아니라 모두 0이다 (COALESCE 검증)")
    void 거래_없는_달() throws Exception {
        mockMvc.perform(monthly("2026-09", "2026-09-16"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.summary.income").value(0))
                .andExpect(jsonPath("$.data.summary.expense").value(0))
                .andExpect(jsonPath("$.data.summary.net").value(0))
                .andExpect(jsonPath("$.data.byCategory").isEmpty())
                // 직전 3개월도 비어 있으므로 예측하지 않는다
                .andExpect(jsonPath("$.data.forecast").doesNotExist())
                .andExpect(jsonPath("$.data.anomalies").isEmpty())
                .andExpect(jsonPath("$.data.budgets").isEmpty())
                // daily 는 거래가 없어도 1일~말일 전부 채운다
                .andExpect(jsonPath("$.data.daily.length()").value(30))
                .andExpect(jsonPath("$.data.daily[0].date").value("2026-09-01"))
                .andExpect(jsonPath("$.data.daily[0].expense").value(0));
    }

    // ---------- 15. 데이터 부족 ----------

    @Test
    @DisplayName("직전 3개월에 거래가 없으면 forecast 가 null 이다")
    void 기준선_데이터가_없으면_예측하지_않는다() throws Exception {
        // 당월에만 거래가 있다
        txn(foodId, "EXPENSE", "50000", "2026-09-10");

        mockMvc.perform(monthly("2026-09", "2026-09-16"))
                .andExpect(jsonPath("$.data.summary.expense").value(50000.00))
                .andExpect(jsonPath("$.data.forecast").doesNotExist());
    }

    @Test
    @DisplayName("1개월치만 있으면 있는 만큼으로 계산하고 basisMonths 에 실제 개월 수를 담는다")
    void 부분_데이터면_basisMonths_로_알린다() throws Exception {
        txn(foodId, "EXPENSE", "920000", "2026-08-15");   // 8월만
        txn(foodId, "EXPENSE", "50000", "2026-09-10");

        mockMvc.perform(monthly("2026-09", "2026-09-16"))
                .andExpect(jsonPath("$.data.forecast.basisMonths").value(1))
                // 920,000 / 92일 = 10,000.00
                .andExpect(jsonPath("$.data.forecast.baselineDailyAvg").value(10000.00));
    }

    // ---------- 16. 런레이트 ----------

    @Test
    @DisplayName("런레이트가 확정 + 일평균 × 남은일수와 소수 둘째 자리까지 일치한다")
    void 런레이트_정확도() throws Exception {
        // 기준선: 6~8월 지출 합계 2,760,000 / 92일 = 30,000.00/일
        txn(foodId, "EXPENSE", "920000", "2026-06-15");
        txn(foodId, "EXPENSE", "920000", "2026-07-15");
        txn(foodId, "EXPENSE", "920000", "2026-08-15");
        // 당월 확정 지출 400,000
        txn(foodId, "EXPENSE", "400000", "2026-09-10");

        String body = perform(monthly("2026-09", "2026-09-16"));
        JsonNode forecast = objectMapper.readTree(body).get("data").get("forecast");

        assertThat(forecast.get("daysInMonth").asInt()).isEqualTo(30);
        assertThat(forecast.get("daysElapsed").asInt()).isEqualTo(16);
        assertThat(forecast.get("basisMonths").asInt()).isEqualTo(3);
        assertThat(forecast.get("baselineDailyAvg").decimalValue()).isEqualByComparingTo("30000.00");
        assertThat(forecast.get("confirmedExpense").decimalValue()).isEqualByComparingTo("400000.00");

        // 400,000 + 30,000 × (30 - 16) = 400,000 + 420,000 = 820,000
        BigDecimal expected = new BigDecimal("400000")
                .add(new BigDecimal("30000").multiply(BigDecimal.valueOf(30 - 16)));
        assertThat(forecast.get("projectedExpense").decimalValue()).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("asOf 가 대상 월 이후면 예측값이 확정값과 같다 (과거 달은 예측하지 않는다)")
    void 과거_달은_예측하지_않는다() throws Exception {
        txn(foodId, "EXPENSE", "920000", "2026-06-15");
        txn(foodId, "EXPENSE", "920000", "2026-07-15");
        txn(foodId, "EXPENSE", "920000", "2026-08-15");
        txn(foodId, "EXPENSE", "400000", "2026-09-10");

        String body = perform(monthly("2026-09", "2026-11-01"));
        JsonNode forecast = objectMapper.readTree(body).get("data").get("forecast");

        assertThat(forecast.get("daysElapsed").asInt()).isEqualTo(30);
        assertThat(forecast.get("projectedExpense").decimalValue())
                .isEqualByComparingTo(forecast.get("confirmedExpense").decimalValue());
    }

    // ---------- 17. 이상치 ----------

    @Test
    @DisplayName("경과 7일 미만이면 anomalies 가 빈 배열이다")
    void 월초에는_이상치를_계산하지_않는다() throws Exception {
        seedAnomalyBaseline();
        txn(foodId, "EXPENSE", "500000", "2026-09-02");   // 속도상 폭증

        mockMvc.perform(monthly("2026-09", "2026-09-06"))
                .andExpect(jsonPath("$.data.anomalies").isEmpty());

        // 7일이 되면 계산한다
        mockMvc.perform(monthly("2026-09", "2026-09-07"))
                .andExpect(jsonPath("$.data.anomalies.length()").value(1));
    }

    @Test
    @DisplayName("이상치에 currentPace·baseline·deltaRatio 가 담긴다")
    void 이상치_내용() throws Exception {
        seedAnomalyBaseline();
        txn(foodId, "EXPENSE", "500000", "2026-09-02");

        String body = perform(monthly("2026-09", "2026-09-10"));
        JsonNode anomaly = objectMapper.readTree(body).get("data").get("anomalies").get(0);

        assertThat(anomaly.get("name").asString()).isEqualTo("식비");
        assertThat(anomaly.get("baseline").decimalValue()).isGreaterThan(BigDecimal.ZERO);
        assertThat(anomaly.get("currentPace").decimalValue()).isGreaterThan(BigDecimal.ZERO);
        assertThat(anomaly.get("deltaRatio").decimalValue().abs())
                .isGreaterThanOrEqualTo(new BigDecimal("0.30"));
    }

    @Test
    @DisplayName("기준선과 거의 같은 속도면 이상치에 담기지 않는다")
    void 평소와_비슷하면_이상치가_아니다() throws Exception {
        // 6~8월 식비 92,000 → 일평균 1,000 → 9월 환산 30,000
        txn(foodId, "EXPENSE", "30000", "2026-06-15");
        txn(foodId, "EXPENSE", "31000", "2026-07-15");
        txn(foodId, "EXPENSE", "31000", "2026-08-15");
        // 9월 15일까지 15,000 → 속도 30,000 (기준선과 동일)
        txn(foodId, "EXPENSE", "15000", "2026-09-10");

        mockMvc.perform(monthly("2026-09", "2026-09-15"))
                .andExpect(jsonPath("$.data.anomalies").isEmpty());
    }

    // ---------- 18. 고정지출 ----------

    @Test
    @DisplayName("3개월 연속 동일 상호를 찾아내고 2개월만 있는 상호는 제외한다")
    void 고정지출_감지() throws Exception {
        // 3개월 연속 — 감지된다
        txn(foodId, "EXPENSE", "17000", "2026-06-05", "넷플릭스");
        txn(foodId, "EXPENSE", "17000", "2026-07-05", "넷플릭스");
        txn(foodId, "EXPENSE", "17000", "2026-08-05", "넷플릭스");
        // 2개월만 — 제외된다
        txn(foodId, "EXPENSE", "12000", "2026-07-10", "왓챠");
        txn(foodId, "EXPENSE", "12000", "2026-08-10", "왓챠");
        // 금액이 ±10% 밖 — 제외된다
        txn(transportId, "EXPENSE", "50000", "2026-06-12", "통신비");
        txn(transportId, "EXPENSE", "50000", "2026-07-12", "통신비");
        txn(transportId, "EXPENSE", "90000", "2026-08-12", "통신비");
        // merchant 없음 — 제외된다
        txn(foodId, "EXPENSE", "8000", "2026-06-20");
        txn(foodId, "EXPENSE", "8000", "2026-07-20");
        txn(foodId, "EXPENSE", "8000", "2026-08-20");

        mockMvc.perform(get(RECURRING).header("Authorization", auth).param("asOf", "2026-09-16"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].merchant").value("넷플릭스"))
                .andExpect(jsonPath("$.data[0].medianAmount").value(17000.00))
                .andExpect(jsonPath("$.data[0].monthsSeen").value(3))
                .andExpect(jsonPath("$.data[0].lastDate").value("2026-08-05"));
    }

    // ---------- 19. asOf 의존성 ----------

    @Test
    @DisplayName("asOf 를 바꾸면 결과가 바뀐다 — 서버 시각에 의존하지 않는다")
    void asOf_에_따라_결과가_바뀐다() throws Exception {
        txn(foodId, "EXPENSE", "920000", "2026-06-15");
        txn(foodId, "EXPENSE", "920000", "2026-07-15");
        txn(foodId, "EXPENSE", "920000", "2026-08-15");
        txn(foodId, "EXPENSE", "400000", "2026-09-10");

        JsonNode day5 = forecastAt("2026-09-05");
        JsonNode day16 = forecastAt("2026-09-16");
        JsonNode day30 = forecastAt("2026-09-30");

        assertThat(day5.get("daysElapsed").asInt()).isEqualTo(5);
        assertThat(day16.get("daysElapsed").asInt()).isEqualTo(16);
        assertThat(day30.get("daysElapsed").asInt()).isEqualTo(30);

        // 남은 일수가 줄수록 예측값이 확정값에 수렴한다
        assertThat(day5.get("projectedExpense").decimalValue())
                .isGreaterThan(day16.get("projectedExpense").decimalValue());
        assertThat(day16.get("projectedExpense").decimalValue())
                .isGreaterThan(day30.get("projectedExpense").decimalValue());
        assertThat(day30.get("projectedExpense").decimalValue())
                .isEqualByComparingTo(day30.get("confirmedExpense").decimalValue());
    }

    @Test
    @DisplayName("StatsService 소스에 now() 계열 호출이 없다")
    void 서비스에_now_호출이_없다() throws IOException {
        // CLAUDE.md 4장 "서버가 이번 달과 오늘을 판정하지 않는다"를 소스 수준에서 확인한다.
        // 테스트로는 "서버 시각을 바꿔도 결과가 같다"를 직접 재현할 수 없으므로 이 검사가 그 역할을 한다.
        for (String file : List.of("StatsService.java", "ForecastCalculator.java", "RecurringDetector.java")) {
            String source = Files.readString(
                    Path.of("src/main/java/com/example/service/" + file), StandardCharsets.UTF_8);

            // 주석은 제외한다. 왜 now() 를 쓰면 안 되는지 설명하는 주석 자체에 그 문자열이 들어 있다.
            String code = source.lines()
                    .map(String::strip)
                    .filter(line -> !line.startsWith("//") && !line.startsWith("*")
                            && !line.startsWith("/*"))
                    .collect(java.util.stream.Collectors.joining("\n"));

            assertThat(code)
                    .as("%s 에 시각 판정 호출이 있으면 안 된다", file)
                    .doesNotContain("LocalDate.now()")
                    .doesNotContain("YearMonth.now()")
                    .doesNotContain("LocalDateTime.now()")
                    .doesNotContain("Instant.now()");
        }
    }

    // ---------- DoD 추가분 ----------

    @Test
    @DisplayName("카테고리 비율의 합이 1.0 ± 0.01 이다")
    void 비율_합() throws Exception {
        txn(foodId, "EXPENSE", "412000", "2026-09-05");
        txn(transportId, "EXPENSE", "330300", "2026-09-06");
        txn(foodId, "EXPENSE", "1100000", "2026-09-07");

        String body = perform(monthly("2026-09", "2026-09-16"));
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode node : objectMapper.readTree(body).get("data").get("byCategory")) {
            sum = sum.add(node.get("ratio").decimalValue());
        }
        assertThat(sum).isBetween(new BigDecimal("0.99"), new BigDecimal("1.01"));
    }

    @Test
    @DisplayName("삭제된 카테고리의 과거 지출이 집계에서 빠지지 않는다")
    void 삭제된_카테고리도_집계에_남는다() throws Exception {
        txn(foodId, "EXPENSE", "412000", "2026-09-05");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete(CATEGORIES + "/" + foodId).header("Authorization", auth))
                .andExpect(status().isOk());

        mockMvc.perform(monthly("2026-09", "2026-09-16"))
                .andExpect(jsonPath("$.data.summary.expense").value(412000.00))
                .andExpect(jsonPath("$.data.byCategory.length()").value(1))
                .andExpect(jsonPath("$.data.byCategory[0].name").value("식비"))
                .andExpect(jsonPath("$.data.byCategory[0].deleted").value(true));
    }

    @Test
    @DisplayName("수입과 지출이 섞여도 summary 가 올바르다")
    void 수입_지출_요약() throws Exception {
        txn(salaryId, "INCOME", "3200000", "2026-09-25");
        txn(foodId, "EXPENSE", "412000", "2026-09-05");

        mockMvc.perform(monthly("2026-09", "2026-09-30"))
                .andExpect(jsonPath("$.data.summary.income").value(3200000.00))
                .andExpect(jsonPath("$.data.summary.expense").value(412000.00))
                .andExpect(jsonPath("$.data.summary.net").value(2788000.00))
                // byCategory 는 지출만 담는다
                .andExpect(jsonPath("$.data.byCategory.length()").value(1));
    }

    @Test
    @DisplayName("yearMonth 형식이 틀리면 500 이 아니라 400 이다")
    void 잘못된_yearMonth() throws Exception {
        mockMvc.perform(get(MONTHLY).header("Authorization", auth)
                        .param("yearMonth", "2026-13").param("asOf", "2026-09-16"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    // ---------- 헬퍼 ----------

    /** 6~8월 식비 92,000 → 일평균 1,000 → 9월 환산 기준선 30,000 */
    private void seedAnomalyBaseline() throws Exception {
        txn(foodId, "EXPENSE", "30000", "2026-06-15");
        txn(foodId, "EXPENSE", "31000", "2026-07-15");
        txn(foodId, "EXPENSE", "31000", "2026-08-15");
    }

    private JsonNode forecastAt(String asOf) throws Exception {
        return objectMapper.readTree(perform(monthly("2026-09", asOf))).get("data").get("forecast");
    }

    private org.springframework.test.web.servlet.RequestBuilder monthly(String yearMonth, String asOf) {
        return get(MONTHLY).header("Authorization", auth)
                .param("yearMonth", yearMonth).param("asOf", asOf);
    }

    private String perform(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        return mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
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
