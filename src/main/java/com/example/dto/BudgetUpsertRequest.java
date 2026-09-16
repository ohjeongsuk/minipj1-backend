package com.example.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 예산은 "카테고리 목록에 금액을 채워 한 번에 저장"하는 형태라
 * 개별 생성/삭제 경로(POST·DELETE)가 필요 없다. PUT upsert 하나로 끝난다.
 */
public record BudgetUpsertRequest(

        @NotBlank(message = "연월은 필수입니다.")
        @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "연월은 yyyy-MM 형식이어야 합니다.")
        String yearMonth,

        @NotNull(message = "항목 목록은 필수입니다.")
        @Valid
        List<Item> items
) {

    /**
     * amount 가 0 또는 null 이면 해당 행을 제거한다(예산 미설정 상태로 되돌림).
     * 그래서 여기서는 0 을 허용한다. 거래 금액과 달리 0 이 의미를 갖는다.
     */
    public record Item(

            @NotNull(message = "카테고리는 필수입니다.")
            Long categoryId,

            @PositiveOrZero(message = "예산은 0 이상이어야 합니다.")
            @DecimalMax(value = "20000000000", message = "예산이 너무 큽니다.")
            BigDecimal amount
    ) {
        /** 행을 제거해야 하는 항목인지 */
        public boolean isRemoval() {
            return amount == null || amount.compareTo(BigDecimal.ZERO) == 0;
        }
    }
}
