package com.example.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 예측 수식 검증. DB 없이 입력 → 출력만 본다.
 * 임계값 경계(29% / 30% / 31%)를 DB 데이터로 만들려면 거래를 수십 건 심어야 하지만
 * 순수 함수면 BigDecimal 두 개로 끝난다.
 */
class ForecastCalculatorTest {

    private static final YearMonth SEP = YearMonth.of(2026, 9);   // 30일

    @Nested
    @DisplayName("기간 계산")
    class Period {

        @Test
        @DisplayName("기준선 구간은 대상 월의 직전 3개월이며 당월을 포함하지 않는다")
        void 기준선_구간() {
            assertThat(ForecastCalculator.baselineStart(SEP)).isEqualTo(LocalDate.of(2026, 6, 1));
            assertThat(ForecastCalculator.baselineEnd(SEP)).isEqualTo(LocalDate.of(2026, 8, 31));
        }

        @Test
        @DisplayName("기준선 총 일수는 개월 수가 아니라 실제 일수다 (6월30 + 7월31 + 8월31 = 92)")
        void 기준선_총일수() {
            assertThat(ForecastCalculator.baselineTotalDays(SEP)).isEqualTo(92);
            // 3월 기준이면 12월31 + 1월31 + 2월28 = 90
            assertThat(ForecastCalculator.baselineTotalDays(YearMonth.of(2026, 3))).isEqualTo(90);
        }

        @Test
        @DisplayName("asOf 가 대상 월 안이면 그 날의 일(day)이 경과일수다")
        void 경과일수_당월() {
            assertThat(ForecastCalculator.daysElapsed(SEP, LocalDate.of(2026, 9, 15))).isEqualTo(15);
        }

        @Test
        @DisplayName("asOf 가 대상 월 이후면 그 달의 총 일수로 간주한다 (과거 달은 예측하지 않는다)")
        void 경과일수_과거달() {
            assertThat(ForecastCalculator.daysElapsed(SEP, LocalDate.of(2026, 10, 3))).isEqualTo(30);
            assertThat(ForecastCalculator.daysElapsed(SEP, LocalDate.of(2027, 1, 1))).isEqualTo(30);
        }

        @Test
        @DisplayName("asOf 가 대상 월보다 앞이면 경과일수는 0이다 (미래 달 조회)")
        void 경과일수_미래달() {
            assertThat(ForecastCalculator.daysElapsed(SEP, LocalDate.of(2026, 8, 20))).isZero();
        }
    }

    @Nested
    @DisplayName("기준선과 런레이트")
    class Baseline {

        @Test
        @DisplayName("일평균 = 직전 3개월 합계 ÷ 그 3개월 총 일수")
        void 일평균() {
            // 2,760,000 / 92일 = 30,000.00
            assertThat(ForecastCalculator.baselineDailyAvg(new BigDecimal("2760000"), 92))
                    .isEqualByComparingTo("30000.00");
        }

        @Test
        @DisplayName("총 일수가 0이면 0을 반환한다 (0으로 나누지 않는다)")
        void 일수가_0이면_0() {
            assertThat(ForecastCalculator.baselineDailyAvg(new BigDecimal("1000"), 0))
                    .isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("3으로 나누는 무한소수도 예외 없이 처리된다 (scale + RoundingMode)")
        void 무한소수도_안전하다() {
            // 10000 / 3 = 3333.333... → HALF_UP, scale 2
            assertThat(ForecastCalculator.baselineDailyAvg(new BigDecimal("10000"), 3))
                    .isEqualByComparingTo("3333.33");
        }

        @Test
        @DisplayName("런레이트 = 확정 + 일평균 × 남은일수 (소수 둘째 자리까지)")
        void 런레이트() {
            // 1,842,300 + 85,300 × (30 - 15) = 1,842,300 + 1,279,500 = 3,121,800
            BigDecimal projected = ForecastCalculator.projectedExpense(
                    new BigDecimal("1842300"), new BigDecimal("85300"), 30, 15);
            assertThat(projected).isEqualByComparingTo("3121800.00");
        }

        @Test
        @DisplayName("경과일수가 총 일수와 같으면 예측값은 확정값과 같다 (과거 달)")
        void 과거달은_예측하지_않는다() {
            BigDecimal projected = ForecastCalculator.projectedExpense(
                    new BigDecimal("1842300"), new BigDecimal("85300"), 30, 30);
            assertThat(projected).isEqualByComparingTo("1842300.00");
        }

        @Test
        @DisplayName("경과일수가 총 일수를 넘어도 남은일수는 음수가 되지 않는다")
        void 남은일수는_음수가_되지_않는다() {
            BigDecimal projected = ForecastCalculator.projectedExpense(
                    new BigDecimal("1000"), new BigDecimal("500"), 30, 45);
            assertThat(projected).isEqualByComparingTo("1000.00");
        }
    }

    @Nested
    @DisplayName("데이터 부족 판정")
    class DataSufficiency {

        @Test
        @DisplayName("직전 3개월에 거래가 한 건도 없으면 예측하지 않는다")
        void 데이터가_없으면_예측_불가() {
            Map<YearMonth, BigDecimal> empty = Map.of(
                    YearMonth.of(2026, 6), BigDecimal.ZERO,
                    YearMonth.of(2026, 7), BigDecimal.ZERO,
                    YearMonth.of(2026, 8), BigDecimal.ZERO);
            assertThat(ForecastCalculator.canForecast(empty)).isFalse();
        }

        @Test
        @DisplayName("한 달이라도 거래가 있으면 있는 만큼으로 예측한다")
        void 한_달만_있어도_예측한다() {
            Map<YearMonth, BigDecimal> partial = Map.of(
                    YearMonth.of(2026, 6), BigDecimal.ZERO,
                    YearMonth.of(2026, 7), BigDecimal.ZERO,
                    YearMonth.of(2026, 8), new BigDecimal("500000"));
            assertThat(ForecastCalculator.canForecast(partial)).isTrue();
            assertThat(ForecastCalculator.basisMonths(
                    List.of(BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("500000")))).isEqualTo(1);
        }

        @Test
        @DisplayName("basisMonths 는 실제로 거래가 있었던 개월 수다")
        void 실제_사용_개월수() {
            assertThat(ForecastCalculator.basisMonths(List.of(
                    new BigDecimal("100"), new BigDecimal("200"), new BigDecimal("300")))).isEqualTo(3);
            assertThat(ForecastCalculator.basisMonths(List.of(
                    new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("300")))).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("이상치")
    class Anomaly {

        @Test
        @DisplayName("카테고리 기준선은 일평균으로 환산한 뒤 당월 일수를 곱한다")
        void 카테고리_기준선() {
            // 1,845,000 / 92일 = 20,054.3478/일 (scale 4) → × 30일 = 601,630.434 → 601,630.43
            BigDecimal baseline = ForecastCalculator.categoryBaseline(
                    new BigDecimal("1845000"), 92, 30);
            assertThat(baseline).isEqualByComparingTo("601630.43");
        }

        @Test
        @DisplayName("currentPace 는 이번 달 지출을 월 환산한 값이다")
        void 현재_속도() {
            // 412,000 / 15일 = 27,466.6667/일 → × 30일 = 824,000.00
            assertThat(ForecastCalculator.currentPace(new BigDecimal("412000"), 15, 30))
                    .isEqualByComparingTo("824000.00");
        }

        @Test
        @DisplayName("경과일수가 0이면 속도를 0으로 둔다 (0으로 나누지 않는다)")
        void 경과일수_0() {
            assertThat(ForecastCalculator.currentPace(new BigDecimal("1000"), 0, 30))
                    .isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("deltaRatio = (currentPace - baseline) / baseline")
        void 증감률() {
            // (824,000 - 615,000) / 615,000 = 0.33983...
            assertThat(ForecastCalculator.deltaRatio(new BigDecimal("824000"), new BigDecimal("615000")))
                    .isEqualByComparingTo("0.3398");
        }

        @Test
        @DisplayName("baseline 이 0이면 증감률을 계산하지 않는다 (Infinity 방지)")
        void 기준선_0은_계산하지_않는다() {
            assertThat(ForecastCalculator.deltaRatio(new BigDecimal("100"), BigDecimal.ZERO)).isNull();
            assertThat(ForecastCalculator.isAnomaly(null)).isFalse();
        }

        @Test
        @DisplayName("임계값은 정확히 30%다 — 29%는 제외, 30%와 31%는 포함")
        void 임계값_경계() {
            assertThat(ForecastCalculator.isAnomaly(new BigDecimal("0.29"))).isFalse();
            assertThat(ForecastCalculator.isAnomaly(new BigDecimal("0.30"))).isTrue();
            assertThat(ForecastCalculator.isAnomaly(new BigDecimal("0.31"))).isTrue();
            // 감소 방향도 절댓값으로 본다
            assertThat(ForecastCalculator.isAnomaly(new BigDecimal("-0.29"))).isFalse();
            assertThat(ForecastCalculator.isAnomaly(new BigDecimal("-0.30"))).isTrue();
        }

        @Test
        @DisplayName("경과 7일 미만이면 이상치를 계산하지 않는다")
        void 월초에는_계산하지_않는다() {
            assertThat(ForecastCalculator.anomalyPeriodReached(6)).isFalse();
            assertThat(ForecastCalculator.anomalyPeriodReached(7)).isTrue();
        }
    }

    @Nested
    @DisplayName("비율")
    class Ratio {

        @Test
        @DisplayName("분모가 0이면 0을 반환한다 (NaN/Infinity 방지)")
        void 분모가_0() {
            assertThat(ForecastCalculator.ratio(new BigDecimal("100"), BigDecimal.ZERO))
                    .isEqualByComparingTo("0");
            assertThat(ForecastCalculator.ratio(new BigDecimal("100"), null))
                    .isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("카테고리 비율의 합이 1.0 ± 0.01 안에 든다")
        void 비율_합() {
            BigDecimal total = new BigDecimal("1842300");
            List<BigDecimal> parts = List.of(
                    new BigDecimal("412000"), new BigDecimal("330300"),
                    new BigDecimal("600000"), new BigDecimal("500000"));

            BigDecimal sum = parts.stream()
                    .map(part -> ForecastCalculator.ratio(part, total))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertThat(sum).isBetween(new BigDecimal("0.99"), new BigDecimal("1.01"));
        }
    }
}
