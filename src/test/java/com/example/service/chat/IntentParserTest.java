package com.example.service.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.example.domain.TransactionType;
import com.example.dto.CategoryResponse;

/**
 * 파서는 순수 함수라 DB 없이 입력→출력만 본다.
 * CsvParserTest·ForecastCalculatorTest 와 같은 방식이다.
 */
class IntentParserTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 9, 17);

    private static CategoryResponse cat(long id, String name, TransactionType type) {
        return new CategoryResponse(id, name, type, "#EF4444", 0, false);
    }

    private static final List<CategoryResponse> CATEGORIES = List.of(
            cat(1L, "식비", TransactionType.EXPENSE),
            cat(2L, "통신", TransactionType.EXPENSE),
            cat(3L, "주거/통신", TransactionType.EXPENSE),
            cat(4L, "급여", TransactionType.INCOME));

    private static Intent parse(String message) {
        return IntentParser.parse(message, AS_OF, CATEGORIES);
    }

    @Nested
    @DisplayName("기간 해석")
    class Period {

        @Test
        @DisplayName("yyyy-MM 을 그대로 읽는다")
        void explicitYearMonth() {
            assertThat(parse("2026-08 얼마 썼어").yearMonth()).isEqualTo(YearMonth.of(2026, 8));
        }

        @Test
        @DisplayName("'8월' 은 asOf 의 연도로 읽는다")
        void monthOnly() {
            assertThat(parse("8월 얼마 썼어").yearMonth()).isEqualTo(YearMonth.of(2026, 8));
        }

        @Test
        @DisplayName("미래 달이어도 연도를 추측하지 않는다")
        void futureMonthKeepsCurrentYear() {
            assertThat(parse("12월 얼마 썼어").yearMonth()).isEqualTo(YearMonth.of(2026, 12));
        }

        @Test
        @DisplayName("'지난달' 은 한 달 전이다")
        void previousMonth() {
            assertThat(parse("지난달 얼마 썼어").yearMonth()).isEqualTo(YearMonth.of(2026, 8));
        }

        @Test
        @DisplayName("기간을 안 쓰면 asOf 의 달이다")
        void defaultsToCurrent() {
            assertThat(parse("얼마 썼어").yearMonth()).isEqualTo(YearMonth.of(2026, 9));
        }
    }

    @Nested
    @DisplayName("카테고리 해석")
    class Category {

        @Test
        @DisplayName("이름이 들어 있으면 카테고리 조회다")
        void matches() {
            Intent intent = parse("지난달 식비 얼마 썼어");
            assertThat(intent.type()).isEqualTo(IntentType.CATEGORY_AMOUNT);
            assertThat(intent.categoryName()).isEqualTo("식비");
        }

        @Test
        @DisplayName("겹치는 이름 중 가장 긴 것을 쓴다")
        void longestWins() {
            assertThat(parse("주거/통신 얼마").categoryName()).isEqualTo("주거/통신");
        }

        @Test
        @DisplayName("수입 카테고리는 type 이 INCOME 으로 담긴다")
        void incomeType() {
            Intent intent = parse("급여 얼마야");
            assertThat(intent.type()).isEqualTo(IntentType.CATEGORY_AMOUNT);
            assertThat(intent.categoryType()).isEqualTo(TransactionType.INCOME);
        }

        @Test
        @DisplayName("없는 이름은 매칭되지 않는다")
        void noMatch() {
            assertThat(parse("술값 얼마 썼어").categoryName()).isNull();
        }
    }

    @Nested
    @DisplayName("의도 판정")
    class Type {

        @Test
        @DisplayName("예산이 카테고리보다 먼저다")
        void budgetBeatsCategory() {
            assertThat(parse("식비 예산 얼마 남았어").type()).isEqualTo(IntentType.BUDGET_STATUS);
        }

        @Test
        @DisplayName("목록 어휘는 최근 내역이다")
        void listWords() {
            assertThat(parse("최근 지출 보여줘").type()).isEqualTo(IntentType.RECENT_TRANSACTIONS);
        }

        @Test
        @DisplayName("금액 어휘만 있으면 월 요약이다")
        void summary() {
            assertThat(parse("이번달 얼마 썼어").type()).isEqualTo(IntentType.MONTHLY_SUMMARY);
        }

        @Test
        @DisplayName("알 수 없는 문장은 UNKNOWN 이다")
        void unknown() {
            assertThat(parse("안녕").type()).isEqualTo(IntentType.UNKNOWN);
        }

        @Test
        @DisplayName("빈 메시지는 UNKNOWN 이다")
        void blank() {
            assertThat(parse("   ").type()).isEqualTo(IntentType.UNKNOWN);
        }
    }

    @Nested
    @DisplayName("건수와 거래 종류")
    class Limit {

        @Test
        @DisplayName("'10건' 을 읽는다")
        void explicit() {
            assertThat(parse("내역 10건 보여줘").limit()).isEqualTo(10);
        }

        @Test
        @DisplayName("안 쓰면 5건이다")
        void defaultsToFive() {
            assertThat(parse("내역 보여줘").limit()).isEqualTo(5);
        }

        @Test
        @DisplayName("큰 수는 20 으로 자른다")
        void clamped() {
            assertThat(parse("내역 1000건 보여줘").limit()).isEqualTo(20);
        }

        @Test
        @DisplayName("'지출' 이 있으면 EXPENSE 로 거른다")
        void expenseOnly() {
            assertThat(parse("최근 지출 보여줘").txnType()).isEqualTo(TransactionType.EXPENSE);
        }

        @Test
        @DisplayName("구분이 없으면 전체다")
        void noFilter() {
            assertThat(parse("최근 내역 보여줘").txnType()).isNull();
        }
    }
}
