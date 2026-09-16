package com.example.domain;

/**
 * 수입/지출 구분. categories.type 과 transactions.type 이 공유한다.
 * 거래의 type 은 카테고리의 type 과 일치해야 하며, 검증은 TransactionService 가 한다.
 */
public enum TransactionType {
    INCOME,
    EXPENSE
}
