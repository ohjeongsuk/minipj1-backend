package com.example.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * budgets 에는 deleted_at 이 없다. 미설정으로 되돌릴 때는 행을 물리 삭제한다.
 */
public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findByUserIdAndYearMonth(Long userId, String yearMonth);

    Optional<Budget> findByUserIdAndCategoryIdAndYearMonth(
            Long userId, Long categoryId, String yearMonth);
}
