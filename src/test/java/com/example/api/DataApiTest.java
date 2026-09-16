package com.example.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.jdbc.Sql;

import tools.jackson.databind.JsonNode;

/**
 * CLAUDE.md 12장 Phase 6 테스트 22~26번 + Phase 6 DoD 추가분.
 */
@Sql(scripts = "classpath:db/schema-extra.sql")
class DataApiTest extends ApiTestSupport {

    private static final String EXPORT = "/api/v1/data/export";
    private static final String IMPORT = "/api/v1/data/import";
    private static final String HEADER_LINE = "날짜,구분,카테고리,금액,거래처,메모";

    private String auth;
    private long foodId;

    @BeforeEach
    void setUp() throws Exception {
        auth = signupAndLogin("data-api@example.com");
        foodId = categoryIdByName("식비");
    }

    // ---------- 23. BOM ----------

    @Test
    @DisplayName("내보낸 CSV 의 첫 3바이트가 EF BB BF 다 (없으면 Excel 에서 한글이 깨진다)")
    void BOM_이_붙는다() throws Exception {
        txn(foodId, "12500", "2026-09-14", "스타벅스 강남점", "팀 미팅");

        byte[] body = exportBytes();

        assertThat(Arrays.copyOf(body, 3))
                .containsExactly((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
    }

    @Test
    @DisplayName("Content-Type 과 Content-Disposition 이 올바르다")
    void 응답_헤더() throws Exception {
        txn(foodId, "12500", "2026-09-14", "스타벅스", null);

        String disposition = mockMvc.perform(get(EXPORT).header("Authorization", auth)
                        .param("from", "2026-09-01").param("to", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andReturn().getResponse().getHeader("Content-Disposition");

        // from·to 가 같은 달이면 moneylog_2026-09.csv
        assertThat(disposition).contains("attachment").contains("moneylog_2026-09.csv");
    }

    @Test
    @DisplayName("내보낸 CSV 본문이 BOM 제외 후 헤더로 시작한다")
    void 내보내기_본문() throws Exception {
        txn(foodId, "12500", "2026-09-14", "스타벅스 강남점", "팀 미팅");

        String text = exportText();

        assertThat(text.lines().findFirst()).hasValue(HEADER_LINE);
        assertThat(text).contains("2026-09-14,지출,식비,12500,스타벅스 강남점,팀 미팅");
    }

    // ---------- 22. 왕복 ----------

    @Test
    @DisplayName("내보낸 CSV 를 그대로 다시 가져오면 건수가 일치한다")
    void 왕복_건수_일치() throws Exception {
        txn(foodId, "12500", "2026-09-14", "스타벅스", "팀 미팅");
        txn(foodId, "3000", "2026-09-15", "GS25", null);
        txn(foodId, "7800", "2026-09-16", null, null);

        byte[] exported = exportBytes();

        mockMvc.perform(upload(exported))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imported").value(3))
                .andExpect(jsonPath("$.data.failed").value(0));

        // 중복 검사를 하지 않으므로 총 6건이 된다 (의도된 동작)
        mockMvc.perform(get(TRANSACTIONS).header("Authorization", auth))
                .andExpect(jsonPath("$.data.totalElements").value(6));
    }

    // ---------- 24. 특수문자 왕복 ----------

    @Test
    @DisplayName("메모에 콤마·따옴표·줄바꿈이 있어도 왕복에서 값이 보존된다")
    void 특수문자_왕복() throws Exception {
        String memo = "팀 미팅, 커피 2잔 \"아메리카노\"\n다음 줄";
        txn(foodId, "12500", "2026-09-14", "스타벅스, 강남점", memo);

        byte[] exported = exportBytes();
        mockMvc.perform(upload(exported))
                .andExpect(jsonPath("$.data.imported").value(1))
                .andExpect(jsonPath("$.data.failed").value(0));

        String body = mockMvc.perform(get(TRANSACTIONS).header("Authorization", auth)
                        .param("size", "10"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        for (JsonNode node : objectMapper.readTree(body).get("data").get("content")) {
            assertThat(node.get("merchant").asString()).isEqualTo("스타벅스, 강남점");
            assertThat(node.get("memo").asString()).isEqualTo(memo);
        }
    }

    // ---------- 25. 부분 성공 ----------

    @Test
    @DisplayName("없는 카테고리가 섞이면 그 행만 실패하고 나머지는 성공한다 (행 번호 포함)")
    void 부분_성공() throws Exception {
        String csv = HEADER_LINE + "\n"
                + "2026-09-14,지출,식비,12500,스타벅스,\n"
                + "2026-09-15,지출,식대,3000,GS25,\n"      // 3행: 없는 카테고리
                + "2026-09-16,지출,식비,7800,쿠팡,\n";

        mockMvc.perform(upload(utf8WithBom(csv)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imported").value(2))
                .andExpect(jsonPath("$.data.failed").value(1))
                .andExpect(jsonPath("$.data.errors[0].line").value(3))
                .andExpect(jsonPath("$.data.errors[0].reason")
                        .value(org.hamcrest.Matchers.containsString("식대")));
    }

    @Test
    @DisplayName("카테고리 구분이 어긋나는 행은 실패한다")
    void 구분_불일치_행_실패() throws Exception {
        String csv = HEADER_LINE + "\n2026-09-14,수입,식비,12500,회사,\n";

        mockMvc.perform(upload(utf8WithBom(csv)))
                .andExpect(jsonPath("$.data.imported").value(0))
                .andExpect(jsonPath("$.data.failed").value(1))
                .andExpect(jsonPath("$.data.errors[0].reason")
                        .value(org.hamcrest.Matchers.containsString("지출 카테고리")));
    }

    // ---------- 26. 엑셀 왕복 내성 ----------

    @Test
    @DisplayName("CP949 로 인코딩된 CSV 의 한글이 깨지지 않는다")
    void CP949_파일도_읽는다() throws Exception {
        String csv = HEADER_LINE + "\n2026-09-14,지출,식비,12500,스타벅스 강남점,팀 미팅\n";

        // ⚠️ 픽스처를 UTF-8 로 만들면 이 테스트는 아무것도 검증하지 못한다.
        // 엑셀이 실제로 쓰는 MS949 바이트를 직접 만든다.
        byte[] ms949 = csv.getBytes(Charset.forName("MS949"));
        assertThat(ms949).isNotEqualTo(csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(upload(ms949))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imported").value(1))
                .andExpect(jsonPath("$.data.failed").value(0));

        mockMvc.perform(get(TRANSACTIONS).header("Authorization", auth))
                .andExpect(jsonPath("$.data.content[0].merchant").value("스타벅스 강남점"))
                .andExpect(jsonPath("$.data.content[0].memo").value("팀 미팅"))
                .andExpect(jsonPath("$.data.content[0].category.name").value("식비"));
    }

    @Test
    @DisplayName("엑셀이 바꾼 천단위 금액과 점 구분 날짜를 읽는다")
    void 엑셀_서식_내성() throws Exception {
        String csv = HEADER_LINE + "\n"
                + "2026.09.14,지출,식비,\"12,500\",스타벅스,\n"
                + "2026/09/15,지출,식비,\"1,234,567\",쿠팡,\n";

        mockMvc.perform(upload(utf8WithBom(csv)))
                .andExpect(jsonPath("$.data.imported").value(2))
                .andExpect(jsonPath("$.data.failed").value(0));

        String body = mockMvc.perform(get(TRANSACTIONS).header("Authorization", auth)
                        .param("sort", "txnDate,asc"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode content = objectMapper.readTree(body).get("data").get("content");

        assertThat(content.get(0).get("txnDate").asString()).isEqualTo("2026-09-14");
        assertThat(content.get(0).get("amount").decimalValue()).isEqualByComparingTo("12500");
        assertThat(content.get(1).get("txnDate").asString()).isEqualTo("2026-09-15");
        assertThat(content.get(1).get("amount").decimalValue()).isEqualByComparingTo("1234567");
    }

    @Test
    @DisplayName("BOM 이 붙은 자기 파일을 다시 읽어도 헤더 매칭이 깨지지 않는다")
    void 자기_파일을_읽는다() throws Exception {
        // BOM 을 제거하지 않으면 첫 헤더가 "﻿날짜" 가 되어 헤더 검증에서 400 이 난다
        mockMvc.perform(upload(utf8WithBom(HEADER_LINE + "\n2026-09-14,지출,식비,1000,,\n")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imported").value(1));
    }

    // ---------- 형식 오류 ----------

    @Test
    @DisplayName("헤더가 다르면 400 INVALID_CSV")
    void 헤더가_다르면_400() throws Exception {
        String csv = "date,type,category,amount,merchant,memo\n2026-09-14,지출,식비,1000,,\n";

        mockMvc.perform(upload(utf8WithBom(csv)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_CSV"));
    }

    @Test
    @DisplayName("5,000행을 넘으면 400 INVALID_CSV")
    void 행수_초과는_400() throws Exception {
        StringBuilder csv = new StringBuilder(HEADER_LINE).append('\n');
        for (int i = 0; i < 5001; i++) {
            csv.append("2026-09-14,지출,식비,1000,,\n");
        }

        mockMvc.perform(upload(utf8WithBom(csv.toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_CSV"))
                .andExpect(jsonPath("$.error.message")
                        .value(org.hamcrest.Matchers.containsString("5,000")));
    }

    @Test
    @DisplayName("빈 파일은 400 INVALID_CSV")
    void 빈_파일은_400() throws Exception {
        mockMvc.perform(upload(new byte[0]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_CSV"));
    }

    @Test
    @DisplayName("빈 줄은 실패로 세지 않고 건너뛴다")
    void 빈_줄은_건너뛴다() throws Exception {
        String csv = HEADER_LINE + "\n2026-09-14,지출,식비,1000,,\n\n\n";

        mockMvc.perform(upload(utf8WithBom(csv)))
                .andExpect(jsonPath("$.data.imported").value(1))
                .andExpect(jsonPath("$.data.failed").value(0));
    }

    // ---------- Phase 12 검증에서 발견한 누락 ----------

    /**
     * ⚠️ 미인증 상태로는 재현되지 않는다. Security 필터가 먼저 401 로 막아
     *    컨트롤러까지 가지 않으므로 반드시 토큰을 넣고 확인한다.
     *
     * ⚠️ 프론트는 FormData 를 쓰므로 화면에서는 이 경로를 밟지 않는다.
     *    그래서 Phase 12 의 실제 호출 검증 전까지 드러나지 않았다.
     */
    @Test
    @DisplayName("multipart 가 아닌 Content-Type 으로 가져오기를 부르면 500 이 아니라 415 다")
    void multipart_아닌_요청은_415() throws Exception {
        mockMvc.perform(post(IMPORT)
                        .header("Authorization", auth)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("날짜,구분,카테고리,금액,거래처,메모"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @DisplayName("415 응답도 ApiResponse 봉투를 지킨다")
    void 오류_응답이_봉투를_지킨다() throws Exception {
        String body = mockMvc.perform(post(IMPORT)
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        JsonNode node = objectMapper.readTree(body);
        assertThat(node.has("success")).isTrue();
        assertThat(node.has("data")).isTrue();
        assertThat(node.has("error")).isTrue();
        assertThat(node.get("data").isNull()).isTrue();
    }

    // ---------- 헬퍼 ----------

    private byte[] exportBytes() throws Exception {
        return mockMvc.perform(get(EXPORT).header("Authorization", auth))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    private String exportText() throws Exception {
        byte[] body = exportBytes();
        return new String(body, 3, body.length - 3, StandardCharsets.UTF_8);
    }

    private byte[] utf8WithBom(String text) {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[3 + body.length];
        result[0] = (byte) 0xEF;
        result[1] = (byte) 0xBB;
        result[2] = (byte) 0xBF;
        System.arraycopy(body, 0, result, 3, body.length);
        return result;
    }

    private org.springframework.test.web.servlet.RequestBuilder upload(byte[] content) {
        return multipart(IMPORT)
                .file(new MockMultipartFile("file", "moneylog.csv", "text/csv", content))
                .header("Authorization", auth);
    }

    private void txn(long categoryId, String amount, String date, String merchant, String memo)
            throws Exception {
        mockMvc.perform(post(TRANSACTIONS).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new java.util.LinkedHashMap<>() {{
                            put("categoryId", categoryId);
                            put("type", "EXPENSE");
                            put("amount", new java.math.BigDecimal(amount));
                            put("txnDate", date);
                            put("merchant", merchant);
                            put("memo", memo);
                        }})))
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
