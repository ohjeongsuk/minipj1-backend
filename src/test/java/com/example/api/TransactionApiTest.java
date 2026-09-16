package com.example.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;

import com.example.domain.TransactionRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import tools.jackson.databind.JsonNode;

/**
 * CLAUDE.md 12장 Phase 4 통합 테스트 6~13번 + Phase 4 DoD 추가분.
 */
@Sql(scripts = "classpath:db/schema-extra.sql")
class TransactionApiTest extends ApiTestSupport {

    @Autowired
    private EntityManager em;

    @Autowired
    private EntityManagerFactory emf;

    @Autowired
    private TransactionRepository transactionRepository;

    private String auth;
    private long expenseCategoryId;
    private long incomeCategoryId;

    @BeforeEach
    void setUp() throws Exception {
        auth = signupAndLogin("txn-api@example.com");
        expenseCategoryId = categoryId("EXPENSE", 0);
        incomeCategoryId = categoryId("INCOME", 0);
    }

    // ---------- 6. 생성 → 목록 ----------

    @Test
    @DisplayName("거래를 만들면 목록이 봉투 + 페이지 필드로 응답하고 카테고리가 함께 내려온다")
    void 목록_응답_형태() throws Exception {
        createTransaction(expenseCategoryId, "EXPENSE", "12500", "2026-09-14", "스타벅스 강남점", "팀 미팅");

        mockMvc.perform(get(TRANSACTIONS).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.error").doesNotExist())
                // PageResponse 는 최상위가 아니라 data 안에 들어간다
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.first").value(true))
                .andExpect(jsonPath("$.data.last").value(true))
                // 카테고리 이름·색이 함께 내려온다
                .andExpect(jsonPath("$.data.content[0].category.name").value("식비"))
                .andExpect(jsonPath("$.data.content[0].category.color").value("#EF4444"))
                .andExpect(jsonPath("$.data.content[0].category.deleted").value(false));
    }

    @Test
    @DisplayName("날짜는 문자열, 금액은 JSON 숫자로 직렬화된다")
    void 직렬화_포맷() throws Exception {
        String body = createTransaction(
                expenseCategoryId, "EXPENSE", "12500", "2026-09-14", "스타벅스", null);
        JsonNode data = objectMapper.readTree(body).get("data");

        // 배열([2026,9,14])이 아니라 문자열이어야 한다
        assertThat(data.get("txnDate").isString()).isTrue();
        assertThat(data.get("txnDate").asString()).isEqualTo("2026-09-14");
        // 문자열로 감싸지 않은 JSON 숫자여야 한다
        assertThat(data.get("amount").isNumber()).isTrue();
        assertThat(data.get("amount").decimalValue()).isEqualByComparingTo("12500.00");
    }

    // ---------- 7. Soft Delete ----------

    @Test
    @DisplayName("삭제하면 목록에서 빠지고 deleted_at 이 기록되며 물리 행은 남는다")
    void 삭제는_소프트_딜리트다() throws Exception {
        long id = idOf(createTransaction(expenseCategoryId, "EXPENSE", "3000", "2026-09-14", "GS25", null));

        mockMvc.perform(delete(TRANSACTIONS + "/" + id).header("Authorization", auth))
                .andExpect(status().isOk());

        mockMvc.perform(get(TRANSACTIONS).header("Authorization", auth))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get(TRANSACTIONS + "/" + id).header("Authorization", auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TRANSACTION_NOT_FOUND"));

        em.flush();
        em.clear();
        var row = transactionRepository.findById(id).orElseThrow();
        assertThat(row.getDeletedAt()).isNotNull();   // 물리 행은 남아 있다
    }

    // ---------- 8. 소유권 ----------

    @Test
    @DisplayName("타 사용자의 거래는 GET·PUT·DELETE 모두 404 (TXN-12)")
    void 남의_거래는_404() throws Exception {
        long id = idOf(createTransaction(expenseCategoryId, "EXPENSE", "3000", "2026-09-14", "GS25", null));
        String other = signupAndLogin("txn-other@example.com");

        mockMvc.perform(get(TRANSACTIONS + "/" + id).header("Authorization", other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TRANSACTION_NOT_FOUND"));

        mockMvc.perform(put(TRANSACTIONS + "/" + id).header("Authorization", other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyOf(expenseCategoryId, "EXPENSE", "1", "2026-09-14", null, null)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete(TRANSACTIONS + "/" + id).header("Authorization", other))
                .andExpect(status().isNotFound());
    }

    // ---------- 9. type 불일치 ----------

    @Test
    @DisplayName("지출 카테고리에 수입 거래를 만들면 400 CATEGORY_TYPE_MISMATCH (TXN-05)")
    void 구분이_어긋나면_400() throws Exception {
        mockMvc.perform(post(TRANSACTIONS).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyOf(expenseCategoryId, "INCOME", "1000", "2026-09-14", null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_TYPE_MISMATCH"));
    }

    // ---------- 10. 금액 ----------

    @Test
    @DisplayName("금액이 0·음수·200억 초과면 400 (TXN-04)")
    void 잘못된_금액은_400() throws Exception {
        for (String amount : List.of("0", "-5000", "20000000001")) {
            mockMvc.perform(post(TRANSACTIONS).header("Authorization", auth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bodyOf(expenseCategoryId, "EXPENSE", amount, "2026-09-14", null, null)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        }
        // 경계값은 통과한다
        mockMvc.perform(post(TRANSACTIONS).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyOf(expenseCategoryId, "EXPENSE", "20000000000", "2026-09-14", null, null)))
                .andExpect(status().isCreated());
    }

    // ---------- 11. 삭제된 카테고리 ----------

    @Test
    @DisplayName("카테고리를 삭제해도 과거 거래는 남고 deleted:true 로 표시된다 (CAT-03)")
    void 삭제된_카테고리의_과거_거래는_남는다() throws Exception {
        createTransaction(expenseCategoryId, "EXPENSE", "12500", "2026-09-14", "스타벅스", null);

        mockMvc.perform(delete(CATEGORIES + "/" + expenseCategoryId).header("Authorization", auth))
                .andExpect(status().isOk());

        // 거래는 그대로 보이고 이름·색도 유지된다
        mockMvc.perform(get(TRANSACTIONS).header("Authorization", auth))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].category.name").value("식비"))
                .andExpect(jsonPath("$.data.content[0].category.color").value("#EF4444"))
                .andExpect(jsonPath("$.data.content[0].category.deleted").value(true));

        // 선택 UI 용 목록에서는 빠진다
        mockMvc.perform(get(CATEGORIES).header("Authorization", auth))
                .andExpect(jsonPath("$.data.length()").value(8));

        // 삭제된 카테고리로 새 거래를 만들 수는 없다
        mockMvc.perform(post(TRANSACTIONS).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyOf(expenseCategoryId, "EXPENSE", "1000", "2026-09-14", null, null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_NOT_FOUND"));
    }

    // ---------- 12. 2차 정렬 키 ----------

    @Test
    @DisplayName("같은 날짜 거래 30건을 페이지로 넘겨도 id 가 중복되거나 누락되지 않는다 (TXN-06)")
    void 페이지네이션에_중복_누락이_없다() throws Exception {
        for (int i = 0; i < 30; i++) {
            createTransaction(expenseCategoryId, "EXPENSE", String.valueOf(1000 + i),
                    "2026-09-14", "가맹점" + i, null);
        }

        List<Long> collected = new ArrayList<>();
        collected.addAll(idsOfPage(0, 10));
        collected.addAll(idsOfPage(1, 10));
        collected.addAll(idsOfPage(2, 10));

        assertThat(collected).hasSize(30);
        // 중복 없음
        assertThat(collected).doesNotHaveDuplicates();
        // 누락 없음 — 30건이 모두 서로 다른 id 로 수집됐다
        assertThat(collected.stream().distinct().count()).isEqualTo(30);
        // id DESC 2차 키가 붙었으므로 내림차순이어야 한다
        assertThat(collected).isSortedAccordingTo((a, b) -> Long.compare(b, a));
    }

    // ---------- 13. N+1 ----------

    @Test
    @DisplayName("목록 조회 쿼리 수가 항목 수에 비례해 늘지 않는다 (join fetch)")
    void N_플러스_1_이_없다() throws Exception {
        for (int i = 0; i < 3; i++) {
            createTransaction(expenseCategoryId, "EXPENSE", "1000", "2026-09-14", "m" + i, null);
        }
        long queriesFor3 = countQueriesOnList();

        for (int i = 3; i < 6; i++) {
            createTransaction(expenseCategoryId, "EXPENSE", "1000", "2026-09-14", "m" + i, null);
        }
        long queriesFor6 = countQueriesOnList();

        // 항목이 2배가 돼도 쿼리 수는 그대로여야 한다.
        // join fetch 가 없으면 카테고리 조회가 건수만큼 추가된다.
        assertThat(queriesFor6).isEqualTo(queriesFor3);
    }

    // ---------- 필터 · 검색 · 정렬 ----------

    @Test
    @DisplayName("대소문자를 섞어 검색해도 찾고, 메모에만 있는 키워드도 검색된다 (TXN-08)")
    void 키워드_검색() throws Exception {
        createTransaction(expenseCategoryId, "EXPENSE", "5000", "2026-09-14", "Starbucks Gangnam", null);
        createTransaction(expenseCategoryId, "EXPENSE", "7000", "2026-09-15", "GS25", "야근 간식");

        // 거래처, 대소문자 무시
        mockMvc.perform(get(TRANSACTIONS).header("Authorization", auth).param("keyword", "STARBUCKS"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].merchant").value("Starbucks Gangnam"));

        // 메모에만 있는 키워드
        mockMvc.perform(get(TRANSACTIONS).header("Authorization", auth).param("keyword", "야근"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].merchant").value("GS25"));

        // 없는 키워드
        mockMvc.perform(get(TRANSACTIONS).header("Authorization", auth).param("keyword", "없는말"))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("기간·구분·카테고리로 필터할 수 있다 (TXN-07)")
    void 필터() throws Exception {
        createTransaction(expenseCategoryId, "EXPENSE", "5000", "2026-08-01", "지난달", null);
        createTransaction(expenseCategoryId, "EXPENSE", "7000", "2026-09-15", "이번달", null);
        createTransaction(incomeCategoryId, "INCOME", "3200000", "2026-09-25", "월급", null);

        mockMvc.perform(get(TRANSACTIONS).header("Authorization", auth)
                        .param("from", "2026-09-01").param("to", "2026-09-30"))
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(get(TRANSACTIONS).header("Authorization", auth).param("type", "INCOME"))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(get(TRANSACTIONS).header("Authorization", auth)
                        .param("categoryId", String.valueOf(incomeCategoryId)))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("허용 목록 밖의 정렬 값에도 500 이 나지 않고 기본 정렬로 동작한다")
    void 잘못된_정렬값은_무해하다() throws Exception {
        createTransaction(expenseCategoryId, "EXPENSE", "5000", "2026-09-14", "GS25", null);

        for (String sort : List.of("foo,desc", "id;drop table", "", "amount,sideways")) {
            mockMvc.perform(get(TRANSACTIONS).header("Authorization", auth).param("sort", sort))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(1));
        }
    }

    @Test
    @DisplayName("필터를 비운 목록 조회를 10회 반복해도 실패하지 않는다 (PgJDBC prepareThreshold 회귀)")
    void 필터_없는_조회를_반복해도_안전하다() throws Exception {
        createTransaction(expenseCategoryId, "EXPENSE", "5000", "2026-09-14", "GS25", null);

        // (:param is null or ...) 패턴을 쓰면 PgJDBC 가 5회째부터 서버 측 prepared statement 로
        // 전환하면서 "$2 매개 변수의 자료형을 알 수 없습니다" 로 터진다.
        // 5회 미만만 돌리면 통과해버리므로 임계값보다 넉넉히 반복한다.
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get(TRANSACTIONS).header("Authorization", auth))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(1));
        }
    }

    // ---------- 수정 ----------

    @Test
    @DisplayName("PUT 은 전체 교체다. merchant·memo 를 누락하면 null 이 된다 (TXN-09)")
    void 수정은_전체_교체다() throws Exception {
        long id = idOf(createTransaction(
                expenseCategoryId, "EXPENSE", "12500", "2026-09-14", "스타벅스", "팀 미팅"));

        mockMvc.perform(put(TRANSACTIONS + "/" + id).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyOf(expenseCategoryId, "EXPENSE", "9900", "2026-09-20", null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(9900.00))
                .andExpect(jsonPath("$.data.txnDate").value("2026-09-20"))
                .andExpect(jsonPath("$.data.merchant").doesNotExist())
                .andExpect(jsonPath("$.data.memo").doesNotExist());
    }

    // ---------- 헬퍼 ----------

    private long countQueriesOnList() throws Exception {
        Statistics statistics = emf.unwrap(SessionFactory.class).getStatistics();
        em.flush();
        em.clear();
        statistics.clear();

        mockMvc.perform(get(TRANSACTIONS).header("Authorization", auth))
                .andExpect(status().isOk());

        return statistics.getPrepareStatementCount();
    }

    private List<Long> idsOfPage(int page, int size) throws Exception {
        String body = mockMvc.perform(get(TRANSACTIONS).header("Authorization", auth)
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(size)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        List<Long> ids = new ArrayList<>();
        objectMapper.readTree(body).get("data").get("content")
                .forEach(node -> ids.add(node.get("id").asLong()));
        return ids;
    }

    private String createTransaction(long categoryId, String type, String amount,
                                     String txnDate, String merchant, String memo) throws Exception {
        return mockMvc.perform(post(TRANSACTIONS).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyOf(categoryId, type, amount, txnDate, merchant, memo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String bodyOf(long categoryId, String type, String amount,
                          String txnDate, String merchant, String memo) {
        return "{\"categoryId\":%d,\"type\":\"%s\",\"amount\":%s,\"txnDate\":\"%s\",\"merchant\":%s,\"memo\":%s}"
                .formatted(categoryId, type, amount, txnDate, quoteOrNull(merchant), quoteOrNull(memo));
    }

    private String quoteOrNull(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private long idOf(String body) {
        return objectMapper.readTree(body).get("data").get("id").asLong();
    }

    private long categoryId(String type, int index) throws Exception {
        String body = mockMvc.perform(get(CATEGORIES).header("Authorization", auth).param("type", type))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).get("data").get(index).get("id").asLong();
    }
}
