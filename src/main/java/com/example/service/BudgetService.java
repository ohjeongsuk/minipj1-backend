package com.example.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.domain.Budget;
import com.example.domain.BudgetRepository;
import com.example.domain.Category;
import com.example.domain.CategoryRepository;
import com.example.domain.TransactionType;
import com.example.domain.User;
import com.example.domain.UserRepository;
import com.example.dto.BudgetResponse;
import com.example.dto.BudgetUpsertRequest;
import com.example.exception.BusinessException;
import com.example.exception.ErrorCode;

@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    public BudgetService(BudgetRepository budgetRepository,
                         CategoryRepository categoryRepository,
                         UserRepository userRepository) {
        this.budgetRepository = budgetRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
    }

    /** 지출 카테고리 전체 + 설정된 금액. 미설정은 amount 가 null 이다 */
    @Transactional(readOnly = true)
    public List<BudgetResponse> findAll(Long userId, String yearMonth) {
        Map<Long, Budget> budgets = new HashMap<>();
        for (Budget budget : budgetRepository.findByUserIdAndYearMonth(userId, yearMonth)) {
            budgets.put(budget.getCategory().getId(), budget);
        }

        return categoryRepository
                .findByUserIdAndTypeAndDeletedAtIsNullOrderBySortOrderAscIdAsc(userId, TransactionType.EXPENSE)
                .stream()
                .map(category -> {
                    Budget budget = budgets.get(category.getId());
                    return new BudgetResponse(category.getId(), category.getName(), category.getColor(),
                            budget == null ? null : budget.getAmount());
                })
                .toList();
    }

    /**
     * upsert. amount 가 0 또는 null 인 항목은 행을 제거한다(미설정 상태로 되돌림).
     *
     * budgets 는 이 프로젝트에서 물리 삭제를 허용하는 유일한 테이블이다.
     * 예산은 "지운다"가 아니라 "미설정으로 되돌린다"가 자연스럽고,
     * 집계에서 과거 이력을 참조하지 않으므로 안전하다.
     */
    @Transactional
    public List<BudgetResponse> upsert(Long userId, BudgetUpsertRequest request) {
        User user = userRepository.getReferenceById(userId);

        for (BudgetUpsertRequest.Item item : request.items()) {
            Category category = categoryRepository
                    .findByIdAndUserIdAndDeletedAtIsNull(item.categoryId(), userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

            Budget existing = budgetRepository
                    .findByUserIdAndCategoryIdAndYearMonth(userId, category.getId(), request.yearMonth())
                    .orElse(null);

            if (item.isRemoval()) {
                if (existing != null) {
                    budgetRepository.delete(existing);
                }
            } else if (existing != null) {
                existing.updateAmount(item.amount());
            } else {
                budgetRepository.save(
                        Budget.create(user, category, request.yearMonth(), item.amount()));
            }
        }

        return findAll(userId, request.yearMonth());
    }
}
