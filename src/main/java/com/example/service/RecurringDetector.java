package com.example.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 고정지출 자동 감지. CLAUDE.md 5장 수식 4)가 정본이다.
 *
 * 사용자가 등록하지 않아도 반복 결제를 찾아내는 것이 이 제품의 핵심 가치 중 하나다.
 * 반복 거래 스케줄러를 만들지 않고 같은 문제를 푸는 방식이라,
 * 미래 데이터 생성·소급 수정 처리를 전부 피한다.
 *
 * ⚠️ 감지 결과를 DB 에 저장하지 않는다. 화면에 "이거 고정지출로 보여요"만 표시한다.
 *    저장하기 시작하면 사용자가 지운 항목이 다음 달에 되살아나는 문제를 처리해야 하고,
 *    그 순간 반복 거래 기능을 만드는 것과 같아진다.
 */
public final class RecurringDetector {

    /** 금액이 중앙값에서 이 비율 이상 벗어나면 고정지출이 아니다 */
    private static final BigDecimal TOLERANCE = new BigDecimal("0.10");

    private static final int MONEY_SCALE = 2;

    private RecurringDetector() {
    }

    /** 감지 대상 원본 한 건 */
    public record Candidate(String merchant, Long categoryId, BigDecimal amount, LocalDate txnDate) {}

    /** 감지 결과 한 건 */
    public record Detected(String merchant, Long categoryId, BigDecimal medianAmount,
                           int monthsSeen, LocalDate lastDate) {}

    /**
     * 상호 정규화. LOWER(TRIM(...)) 후 공백·괄호·숫자를 제거한다.
     * "넷플릭스 (2)" 와 "넷플릭스" 를 같은 항목으로 묶기 위해서다.
     */
    public static String normalize(String merchant) {
        if (merchant == null) {
            return "";
        }
        return merchant.trim().toLowerCase()
                .replaceAll("[\\s()\\[\\]{}0-9]", "");
    }

    /**
     * @param candidates  스캔 구간의 거래들 (merchant 가 비어 있는 건은 호출자가 걸러도 되고 여기서 걸러도 된다)
     * @param scanMonths  검사할 개월들. 모든 달에 1건 이상 있어야 감지된다
     */
    public static List<Detected> detect(List<Candidate> candidates, Set<YearMonth> scanMonths) {
        if (scanMonths.isEmpty()) {
            return List.of();
        }

        // merchant 가 비어 있는 거래는 대상에서 제외한다
        Map<String, List<Candidate>> grouped = candidates.stream()
                .filter(c -> !normalize(c.merchant()).isEmpty())
                .collect(Collectors.groupingBy(
                        c -> normalize(c.merchant()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<Detected> result = new ArrayList<>();
        for (List<Candidate> group : grouped.values()) {
            Detected detected = evaluate(group, scanMonths);
            if (detected != null) {
                result.add(detected);
            }
        }
        // 금액이 큰 순으로 보여주는 것이 사용자에게 유용하다
        result.sort(Comparator.comparing(Detected::medianAmount).reversed());
        return result;
    }

    private static Detected evaluate(List<Candidate> group, Set<YearMonth> scanMonths) {
        Set<YearMonth> seen = group.stream()
                .map(c -> YearMonth.from(c.txnDate()))
                .filter(scanMonths::contains)
                .collect(Collectors.toSet());

        // 스캔 구간의 모든 달에 1건 이상 있어야 한다. 2개월만 있는 상호는 제외된다.
        if (!seen.containsAll(scanMonths)) {
            return null;
        }

        List<BigDecimal> amounts = group.stream()
                .filter(c -> scanMonths.contains(YearMonth.from(c.txnDate())))
                .map(Candidate::amount)
                .sorted()
                .toList();

        BigDecimal median = median(amounts);
        if (median.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        // 모든 건이 중앙값 ±10% 이내여야 한다. 한 달이라도 벗어나면 고정지출이 아니다.
        BigDecimal allowed = median.multiply(TOLERANCE);
        boolean withinTolerance = amounts.stream()
                .allMatch(amount -> amount.subtract(median).abs().compareTo(allowed) <= 0);
        if (!withinTolerance) {
            return null;
        }

        Candidate latest = group.stream()
                .filter(c -> scanMonths.contains(YearMonth.from(c.txnDate())))
                .max(Comparator.comparing(Candidate::txnDate))
                .orElseThrow();

        return new Detected(latest.merchant(), latest.categoryId(), median, seen.size(), latest.txnDate());
    }

    /** 정렬된 목록의 중앙값. 짝수 개면 가운데 두 값의 평균 */
    static BigDecimal median(List<BigDecimal> sorted) {
        if (sorted.isEmpty()) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE);
        }
        int size = sorted.size();
        if (size % 2 == 1) {
            return sorted.get(size / 2).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        return sorted.get(size / 2 - 1)
                .add(sorted.get(size / 2))
                .divide(BigDecimal.valueOf(2), MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
