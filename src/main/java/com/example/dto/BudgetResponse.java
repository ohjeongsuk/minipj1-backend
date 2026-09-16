package com.example.dto;

import java.math.BigDecimal;

/**
 * 지출 카테고리 전체를 반환하고 설정된 것만 금액을 채운다.
 * 화면이 표를 그대로 그릴 수 있게 하기 위해서다.
 * 미설정 카테고리는 amount 가 null 이다(0 과 구분한다).
 */
public record BudgetResponse(Long categoryId, String name, String color, BigDecimal amount) {
}
