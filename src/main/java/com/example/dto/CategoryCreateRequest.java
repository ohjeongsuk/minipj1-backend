package com.example.dto;

import com.example.domain.TransactionType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CategoryCreateRequest(

        @NotBlank(message = "카테고리 이름은 필수입니다.")
        @Size(min = 1, max = 30, message = "카테고리 이름은 1~30자여야 합니다.")
        String name,

        @NotNull(message = "구분은 필수입니다.")
        TransactionType type,

        // 임의 문자열이 인라인 스타일로 들어가지 않게 형식을 고정한다
        @NotBlank(message = "색상은 필수입니다.")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "색상은 #RRGGBB 형식이어야 합니다.")
        String color,

        @PositiveOrZero(message = "표시 순서는 0 이상이어야 합니다.")
        Integer sortOrder
) {

    /** sortOrder 는 선택 항목이다. 생략하면 0 */
    public int sortOrderOrZero() {
        return sortOrder == null ? 0 : sortOrder;
    }
}
