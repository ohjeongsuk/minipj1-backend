package com.example.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * ⚠️ type 필드를 두지 않는다. 의도적인 누락이다.
 *
 * 수입 카테고리를 지출로 바꾸면 이미 쌓인 거래의 집계가 통째로 뒤집힌다.
 * 서비스에서 막는 대신 요청 DTO 에 아예 필드를 두지 않아, API 스펙 수준에서 불가능하게 만든다.
 */
public record CategoryUpdateRequest(

        @NotBlank(message = "카테고리 이름은 필수입니다.")
        @Size(min = 1, max = 30, message = "카테고리 이름은 1~30자여야 합니다.")
        String name,

        @NotBlank(message = "색상은 필수입니다.")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "색상은 #RRGGBB 형식이어야 합니다.")
        String color,

        @PositiveOrZero(message = "표시 순서는 0 이상이어야 합니다.")
        Integer sortOrder
) {

    public int sortOrderOrZero() {
        return sortOrder == null ? 0 : sortOrder;
    }
}
