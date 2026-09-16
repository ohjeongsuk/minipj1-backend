package com.example.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 목록·집계 쿼리는 Phase 4~5 에서 추가한다.
 * 거래 목록은 카테고리 이름·색을 함께 그리므로 join fetch 가 필요하다(N+1 방지).
 */
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /** 소유권 검증용. 불일치 시 서비스가 404 로 응답한다 */
    Optional<Transaction> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    /** 거래가 하나라도 있는 카테고리는 물리 삭제하지 않는다는 규칙의 근거 */
    boolean existsByCategoryIdAndDeletedAtIsNull(Long categoryId);
}
