package com.example.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.persistence.EntityManager;

class CategoryRepositoryTest extends RepositoryTestSupport {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private EntityManager em;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.create("owner@example.com", "{bcrypt}dummy", "테스터");
        em.persist(user);
        em.flush();
    }

    @Test
    @DisplayName("선택 UI 용 목록은 sortOrder ASC, id ASC 로 정렬되고 삭제된 것은 빠진다")
    void 목록은_정렬되고_삭제분을_제외한다() {
        Category food = persist("식비", 1);
        Category transport = persist("교통", 0);
        Category gone = persist("사라질것", 2);
        gone.softDelete();
        em.flush();

        assertThat(categoryRepository.findByUserIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user.getId()))
                .extracting(Category::getName)
                .containsExactly(transport.getName(), food.getName());
    }

    @Test
    @DisplayName("삭제한 카테고리를 쓰던 과거 거래는 목록에서 사라지지 않고 이름·색도 그대로 보인다")
    void 삭제된_카테고리의_과거_거래는_남는다() {
        Category category = persist("식비", 0);
        Transaction txn = transactionRepository.save(Transaction.create(
                user, category, TransactionType.EXPENSE,
                new BigDecimal("12500"), LocalDate.of(2026, 9, 14), "스타벅스", null));
        em.flush();

        category.softDelete();
        em.flush();
        em.clear();

        // @SQLRestriction 을 Category 에 걸었다면 여기서 거래가 사라지거나 조인이 터진다
        Transaction found = transactionRepository.findById(txn.getId()).orElseThrow();
        assertThat(found.getCategory().getName()).isEqualTo("식비");
        assertThat(found.getCategory().getColor()).isEqualTo("#EF4444");
        assertThat(found.getCategory().isDeleted()).isTrue();

        // 반면 선택 UI 용 목록에서는 빠져야 한다
        assertThat(categoryRepository.findByUserIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(user.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("거래가 있는 카테고리인지 확인할 수 있다 (물리 삭제 금지 근거)")
    void 거래_보유_여부를_확인한다() {
        Category category = persist("식비", 0);
        em.flush();

        assertThat(transactionRepository.existsByCategoryIdAndDeletedAtIsNull(category.getId())).isFalse();

        transactionRepository.save(Transaction.create(user, category, TransactionType.EXPENSE,
                new BigDecimal("12500"), LocalDate.of(2026, 9, 14), null, null));
        em.flush();

        assertThat(transactionRepository.existsByCategoryIdAndDeletedAtIsNull(category.getId())).isTrue();
    }

    private Category persist(String name, int sortOrder) {
        Category category = Category.create(user, name, TransactionType.EXPENSE, "#EF4444", sortOrder);
        em.persist(category);
        return category;
    }
}
