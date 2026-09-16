package com.example.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Spring 의 Page 를 그대로 반환하지 않고 이 DTO 로 변환해 ApiResponse.data 에 담는다.
 * Page 를 직렬화하면 내부 구조(pageable, sort 등)가 그대로 노출되고 Spring 버전에 따라 형태가 바뀐다.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }

    /** 엔티티 Page 를 DTO 로 변환하면서 감싼다 */
    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return from(page.map(mapper));
    }
}
