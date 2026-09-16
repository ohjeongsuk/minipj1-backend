package com.example.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.example.service.RecurringDetector;

/**
 * 고정지출 감지 결과 한 건.
 *
 * 이 결과를 DB 에 저장하지 않는다. 화면에 "이거 고정지출로 보여요"만 표시한다.
 * 저장하면 사용자가 지운 항목이 다음 달에 되살아나는 문제를 처리해야 하고,
 * 그 순간 반복 거래 기능을 만드는 것과 같아진다.
 */
public record RecurringResponse(String merchant,
                                Long categoryId,
                                BigDecimal medianAmount,
                                int monthsSeen,
                                LocalDate lastDate) {

    public static RecurringResponse from(RecurringDetector.Detected detected) {
        return new RecurringResponse(
                detected.merchant(),
                detected.categoryId(),
                detected.medianAmount(),
                detected.monthsSeen(),
                detected.lastDate());
    }
}
