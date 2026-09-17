package com.example.service.chat;

/** 챗봇이 알아듣는 의도. 이 밖의 질문은 전부 UNKNOWN 이다. */
public enum IntentType {
    MONTHLY_SUMMARY,
    CATEGORY_AMOUNT,
    RECENT_TRANSACTIONS,
    BUDGET_STATUS,
    UNKNOWN
}
