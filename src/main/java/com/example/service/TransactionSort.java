package com.example.service;

import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * 정렬 파라미터를 화이트리스트로 거른다.
 *
 * ⚠️ Pageable 을 컨트롤러 파라미터로 그대로 받으면 안 되는 이유가 여기 있다.
 *    Spring 의 PageableHandlerMethodArgumentResolver 는 ?sort=foo,desc 의 foo 를
 *    그대로 Sort 에 담고, 없는 프로퍼티에서 Hibernate 가 터져 500 이 난다.
 *
 * ⚠️ id DESC 를 항상 2차 키로 덧붙인다.
 *    txnDate 가 같은 거래가 흔한데(같은 날 여러 건) 정렬이 하나뿐이면
 *    DB 가 행 순서를 보장하지 않아 페이지를 넘길 때 항목이 중복되거나 누락된다.
 *    createdAt 으로 정렬하던 표본 프로젝트에서는 드러나지 않던 문제다.
 */
public final class TransactionSort {

    /** 엔티티에 실제로 존재하는 프로퍼티만 허용한다 */
    private static final Set<String> ALLOWED = Set.of("txnDate", "amount", "createdAt");

    private static final String DEFAULT_PROPERTY = "txnDate";
    private static final int MAX_SIZE = 100;

    private TransactionSort() {
    }

    /**
     * @param sort "property,direction" 형태. 허용 목록 밖이거나 형식이 틀리면 기본값으로 대체한다
     */
    public static PageRequest toPageRequest(Integer page, Integer size, String sort) {
        int pageNumber = (page == null || page < 0) ? 0 : page;
        int pageSize = (size == null || size < 1) ? 20 : Math.min(size, MAX_SIZE);

        String property = DEFAULT_PROPERTY;
        Sort.Direction direction = Sort.Direction.DESC;

        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            String candidate = parts[0].trim();
            if (ALLOWED.contains(candidate)) {
                property = candidate;
                if (parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())) {
                    direction = Sort.Direction.ASC;
                }
            }
            // 허용 목록 밖이면 조용히 기본값을 쓴다. 400 을 내지 않는 이유는
            // 정렬은 부가 기능이고, 잘못된 값 때문에 목록 전체가 막힐 이유가 없기 때문이다.
        }

        Sort resolved = Sort.by(direction, property).and(Sort.by(Sort.Direction.DESC, "id"));
        return PageRequest.of(pageNumber, pageSize, resolved);
    }
}
