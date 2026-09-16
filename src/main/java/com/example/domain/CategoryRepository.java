package com.example.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 삭제 필터링은 용도별로 다르다.
 * - 선택 UI 용 목록: deleted_at IS NULL 을 건다(삭제한 카테고리를 새 거래에 고를 수 없어야 한다)
 * - 거래 조회 시 조인 / 집계 그룹핑: 걸지 않는다(과거 내역의 이름·색을 그대로 보여준다)
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** 선택 UI 용 목록. 정렬은 sortOrder ASC, id ASC 고정 */
    List<Category> findByUserIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(Long userId);

    List<Category> findByUserIdAndTypeAndDeletedAtIsNullOrderBySortOrderAscIdAsc(
            Long userId, TransactionType type);

    /** 소유권 검증용. 삭제된 것은 대상에서 제외한다 */
    Optional<Category> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    /** 이름 중복 사전 확인. 최종 방어선은 부분 유니크 인덱스다 */
    boolean existsByUserIdAndNameAndTypeAndDeletedAtIsNull(
            Long userId, String name, TransactionType type);
}
