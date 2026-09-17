package com.example.service.chat;

import java.time.YearMonth;

import com.example.domain.TransactionType;

/**
 * 파싱 결과.
 *
 * @param categoryType 맞은 카테고리의 종류. 이 값이 어떤 집계를 부를지 정한다
 * @param txnType      최근 내역을 거를 거래 종류. null 이면 전체
 * @param limit        최근 내역 건수. 다른 의도에서는 0
 */
public record Intent(
        IntentType type,
        YearMonth yearMonth,
        String categoryName,
        TransactionType categoryType,
        TransactionType txnType,
        int limit
) {
    public static Intent unknown() {
        return new Intent(IntentType.UNKNOWN, null, null, null, null, 0);
    }
}
