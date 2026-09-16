package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.example.service.RecurringDetector.Candidate;
import com.example.service.RecurringDetector.Detected;

class RecurringDetectorTest {

    /** asOf 가 2026-09 일 때의 스캔 구간 (당월 제외 직전 3개월) */
    private static final Set<YearMonth> SCAN = Set.of(
            YearMonth.of(2026, 6), YearMonth.of(2026, 7), YearMonth.of(2026, 8));

    @Nested
    @DisplayName("상호 정규화")
    class Normalize {

        @Test
        @DisplayName("대소문자·앞뒤 공백·내부 공백·괄호·숫자를 제거한다")
        void 정규화_규칙() {
            assertThat(RecurringDetector.normalize("  넷플릭스 (2) ")).isEqualTo("넷플릭스");
            assertThat(RecurringDetector.normalize("NETFLIX")).isEqualTo("netflix");
            assertThat(RecurringDetector.normalize("GS25 역삼점 1호")).isEqualTo("gs역삼점호");
        }

        @Test
        @DisplayName("null 과 공백만 있는 상호는 빈 문자열이 된다")
        void 빈_상호() {
            assertThat(RecurringDetector.normalize(null)).isEmpty();
            assertThat(RecurringDetector.normalize("   ")).isEmpty();
            assertThat(RecurringDetector.normalize("(123)")).isEmpty();
        }
    }

    @Nested
    @DisplayName("중앙값")
    class Median {

        @Test
        @DisplayName("홀수 개면 가운데 값")
        void 홀수() {
            assertThat(RecurringDetector.median(List.of(
                    new BigDecimal("100"), new BigDecimal("200"), new BigDecimal("300"))))
                    .isEqualByComparingTo("200.00");
        }

        @Test
        @DisplayName("짝수 개면 가운데 두 값의 평균")
        void 짝수() {
            assertThat(RecurringDetector.median(List.of(
                    new BigDecimal("100"), new BigDecimal("200"),
                    new BigDecimal("300"), new BigDecimal("500"))))
                    .isEqualByComparingTo("250.00");
        }

        @Test
        @DisplayName("빈 목록이면 0")
        void 빈_목록() {
            assertThat(RecurringDetector.median(List.of())).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("감지")
    class Detect {

        @Test
        @DisplayName("3개월 연속 같은 상호·같은 금액이면 고정지출로 감지된다")
        void 세달_연속이면_감지된다() {
            List<Candidate> candidates = List.of(
                    candidate("넷플릭스", 1L, "17000", "2026-06-05"),
                    candidate("넷플릭스", 1L, "17000", "2026-07-05"),
                    candidate("넷플릭스", 1L, "17000", "2026-08-05"));

            List<Detected> result = RecurringDetector.detect(candidates, SCAN);

            assertThat(result).hasSize(1);
            Detected netflix = result.get(0);
            assertThat(netflix.merchant()).isEqualTo("넷플릭스");
            assertThat(netflix.categoryId()).isEqualTo(1L);
            assertThat(netflix.medianAmount()).isEqualByComparingTo("17000.00");
            assertThat(netflix.monthsSeen()).isEqualTo(3);
            assertThat(netflix.lastDate()).isEqualTo(LocalDate.of(2026, 8, 5));
        }

        @Test
        @DisplayName("2개월만 있는 상호는 제외된다")
        void 두달만_있으면_제외된다() {
            List<Candidate> candidates = List.of(
                    candidate("왓챠", 1L, "12000", "2026-07-10"),
                    candidate("왓챠", 1L, "12000", "2026-08-10"));

            assertThat(RecurringDetector.detect(candidates, SCAN)).isEmpty();
        }

        @Test
        @DisplayName("금액이 중앙값 ±10% 를 벗어나는 달이 섞이면 감지되지 않는다")
        void 금액이_흔들리면_제외된다() {
            // 중앙값 17,000 → 허용 범위 15,300 ~ 18,700. 30,000 은 벗어난다
            List<Candidate> candidates = List.of(
                    candidate("넷플릭스", 1L, "17000", "2026-06-05"),
                    candidate("넷플릭스", 1L, "17000", "2026-07-05"),
                    candidate("넷플릭스", 1L, "30000", "2026-08-05"));

            assertThat(RecurringDetector.detect(candidates, SCAN)).isEmpty();
        }

        @Test
        @DisplayName("±10% 경계 안쪽 변동은 허용된다 (요금 인상 등)")
        void 소폭_변동은_허용된다() {
            // 중앙값 17,000 → 허용 15,300 ~ 18,700
            List<Candidate> candidates = List.of(
                    candidate("넷플릭스", 1L, "15500", "2026-06-05"),
                    candidate("넷플릭스", 1L, "17000", "2026-07-05"),
                    candidate("넷플릭스", 1L, "18500", "2026-08-05"));

            assertThat(RecurringDetector.detect(candidates, SCAN)).hasSize(1);
        }

        @Test
        @DisplayName("merchant 가 비어 있는 거래는 후보에 들어가지 않는다")
        void 상호가_없으면_제외된다() {
            List<Candidate> candidates = new ArrayList<>();
            for (String date : List.of("2026-06-05", "2026-07-05", "2026-08-05")) {
                candidates.add(candidate(null, 1L, "17000", date));
                candidates.add(candidate("   ", 1L, "17000", date));
            }

            assertThat(RecurringDetector.detect(candidates, SCAN)).isEmpty();
        }

        @Test
        @DisplayName("표기가 조금 달라도 정규화 후 같으면 한 항목으로 묶인다")
        void 표기가_달라도_묶인다() {
            List<Candidate> candidates = List.of(
                    candidate("넷플릭스", 1L, "17000", "2026-06-05"),
                    candidate("넷플릭스 (2)", 1L, "17000", "2026-07-05"),
                    candidate(" 넷플릭스  ", 1L, "17000", "2026-08-05"));

            assertThat(RecurringDetector.detect(candidates, SCAN)).hasSize(1);
        }

        @Test
        @DisplayName("스캔 구간 밖의 거래는 개월 수에 포함되지 않는다")
        void 구간_밖은_세지_않는다() {
            // 9월(당월)에 2건이 있어도 6월이 비어 있으면 감지되지 않는다
            List<Candidate> candidates = List.of(
                    candidate("넷플릭스", 1L, "17000", "2026-07-05"),
                    candidate("넷플릭스", 1L, "17000", "2026-08-05"),
                    candidate("넷플릭스", 1L, "17000", "2026-09-05"));

            assertThat(RecurringDetector.detect(candidates, SCAN)).isEmpty();
        }

        @Test
        @DisplayName("여러 고정지출이 금액 내림차순으로 정렬된다")
        void 금액_내림차순() {
            List<Candidate> candidates = new ArrayList<>();
            for (String date : List.of("2026-06-05", "2026-07-05", "2026-08-05")) {
                candidates.add(candidate("넷플릭스", 1L, "17000", date));
                candidates.add(candidate("통신비", 2L, "45000", date));
                candidates.add(candidate("헬스장", 3L, "55000", date));
            }

            assertThat(RecurringDetector.detect(candidates, SCAN))
                    .extracting(Detected::merchant)
                    .containsExactly("헬스장", "통신비", "넷플릭스");
        }
    }

    private static Candidate candidate(String merchant, Long categoryId, String amount, String date) {
        return new Candidate(merchant, categoryId, new BigDecimal(amount), LocalDate.parse(date));
    }
}
