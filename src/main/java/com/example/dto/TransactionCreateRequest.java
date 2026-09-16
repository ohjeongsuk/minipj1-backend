package com.example.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.example.domain.TransactionType;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransactionCreateRequest(

        @NotNull(message = "카테고리는 필수입니다.")
        Long categoryId,

        @NotNull(message = "구분은 필수입니다.")
        TransactionType type,

        /*
         * 부호는 type 으로만 표현한다. 음수를 허용하면 "지출 -5000"이 환불인지 입력 실수인지
         * 알 수 없고 집계가 이중 의미를 갖는다. 환불은 반대 type 의 거래로 기록한다.
         * 상한 200억은 오타 방어선이다. DB 의 NUMERIC(15,2) 상한은 이보다 훨씬 크다.
         */
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
