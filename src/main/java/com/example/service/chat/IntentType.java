package com.example.service.chat;

/** 챗봇이 알아듣는 의도. 이 밖의 질문은 전부 UNKNOWN 이다. */
public enum IntentType {
    MONTHLY_SUMMARY,
    CATEGORY_AMOUNT,
    RECENT_TRANSACTIONS,
    BUDGET_STATUS,
    /** 이번 달 예상 지출(런레이트) */
    FORECAST,
    /** 고정지출 자동 감지 */
    RECURRING,
    /** 특정 하루의 금액 */
    DAILY_AMOUNT,
    UNKNOWN
}
