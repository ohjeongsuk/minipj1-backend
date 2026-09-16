package com.example.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.example.domain.TransactionType;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * PUT 은 부분 수정이 아니라 전체 교체다.
 * merchant·memo 를 누락하면 null 로 저장된다(값 삭제로 취급).
 * amount·txnDate·categoryId·type 은 필수이므로 누락 시 400 이다.
 */
public record TransactionUpdateRequest(

        @NotNull(message = "카테고리는 필수입니다.")
        Long categoryId,

        @NotNull(message = "구분은 필수입니다.")
        TransactionType type,

        @NotNull(message = "금액은 필수입니다.")
        @DecimalMin(value = "0", inclusive = false, message = "금액은 0보다 커야 합니다.")
        @DecimalMax(value = "20000000000", message = "금액이 너무 큽니다.")
        BigDecimal amount,

        @NotNull(message = "날짜는 필수입니다.")
        LocalDate txnDate,

        @Size(max = 100, message = "거래처는 100자 이하여야 합니다.")
        String merchant,

        @Size(max = 500, message = "메모는 500자 이하여야 합니다.")
        String memo
) {
}
