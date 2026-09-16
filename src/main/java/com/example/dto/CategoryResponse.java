package com.example.dto;

import com.example.domain.Category;
import com.example.domain.TransactionType;

/**
 * deleted 플래그를 담는 이유는, 삭제된 카테고리가 붙은 과거 거래를
 * 화면에서 이름 옆에 "(삭제됨)"으로 표시해야 하기 때문이다(CAT-03).
 */
public record CategoryResponse(
        Long id,
        String name,
        TransactionType type,
        String color,
        int sortOrder,
        boolean deleted
) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getType(),
                category.getColor(),
                category.getSortOrder(),
                category.isDeleted());
    }
}
