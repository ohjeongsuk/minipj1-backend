package com.example.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.hibernate.exception.ConstraintViolationException;

import jakarta.persistence.EntityManager;

/**
 * db/schema-extra.sql 이 거는 DB 제약을 검증한다.
 * 이 제약들은 Hibernate 가 만들지 못하므로, 스크립트가 실제로 적용됐는지 확인하는 것이 목적이다.
 *
 * 제약 검증을 이 한 클래스에 모은 이유는 @Sql 부착을 빠뜨릴 지점을 줄이기 위해서다.
 * 흩어두면 어느 클래스에서 애노테이션이 빠졌는지 알아채기 어렵고, 그 테스트는 조용히 통과해버린다.
 */
class SchemaConstraintTest extends RepositoryTestSupport {

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
    @DisplayName("같은 이름의 카테고리를 삭제하지 않고 중복 생성하면 부분 유니크 인덱스에 걸린다")
    void 중복_카테고리_생성은_거부된다() {
        em.persist(Category.create(user, "식비", TransactionType.EXPENSE, "#EF4444", 0));
        em.flush();

        // ⚠️ GenerationType.IDENTITY 는 persist() 시점에 INSERT 를 즉시 실행한다.
        // flush() 만 단언 안에 두면 예외가 그 전에 터져 테스트 밖으로 샌다.
        assertThatThrownBy(() -> {
            em.persist(Category.create(user, "식비", TransactionType.EXPENSE, "#EF4444", 1));
            em.flush();
        })
                .isInstanceOf(ConstraintViolationException.class)
                .hasRootCauseInstanceOf(SQLException.class);
    }

    @Test
    @DisplayName("카테고리를 삭제하면 같은 이름을 다시 만들 수 있다 (CAT-04)")
    void 삭제한_이름은_재사용할_수_있다() {
        Category first = Category.create(user, "식비", TransactionType.EXPENSE, "#EF4444", 0);
        em.persist(first);
        em.flush();

        first.softDelete();
        em.flush();

        Category second = Category.create(user, "식비", TransactionType.EXPENSE, "#F59E0B", 0);
        em.persist(second);
        em.flush();   // 부분 인덱스라 예외가 나지 않아야 한다

        assertThat(second.getId()).isNotNull().isNotEqualTo(first.getId());
        assertThat(first.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("같은 이름이라도 구분(type)이 다르면 함께 존재할 수 있다")
    void 구분이_다르면_같은_이름을_쓸_수_있다() {
        em.persist(Category.create(user, "기타", TransactionType.EXPENSE, "#737373", 0));
        em.persist(Category.create(user, "기타", TransactionType.INCOME, "#737373", 0));

        em.flush();   // 인덱스 키에 type 이 포함되므로 예외가 나지 않아야 한다
    }

    @Test
    @DisplayName("거래 금액이 0 이면 CHECK 제약에 걸린다")
    void 금액_0_은_거부된다() {
        Category category = persistCategory();
        assertThatThrownBy(() -> {
            em.persist(Transaction.create(user, category, TransactionType.EXPENSE,
                BigDecimal.ZERO, LocalDate.of(2026, 9, 14), null, null));
            em.flush();
        })
                .isInstanceOf(ConstraintViolationException.class)
                .hasRootCauseInstanceOf(SQLException.class);
    }

    @Test
    @DisplayName("거래 금액이 음수면 CHECK 제약에 걸린다")
    void 음수_금액은_거부된다() {
        Category category = persistCategory();
        assertThatThrownBy(() -> {
            em.persist(Transaction.create(user, category, TransactionType.EXPENSE,
                new BigDecimal("-5000"), LocalDate.of(2026, 9, 14), null, null));
            em.flush();
        })
                .isInstanceOf(ConstraintViolationException.class)
                .hasRootCauseInstanceOf(SQLException.class);
    }

    @Test
    @DisplayName("예산 금액이 0 이면 CHECK 제약에 걸린다")
    void 예산_금액_0_은_거부된다() {
        Category category = persistCategory();
        assertThatThrownBy(() -> {
            em.persist(Budget.create(user, category, "2026-09", BigDecimal.ZERO));
            em.flush();
        })
                .isInstanceOf(ConstraintViolationException.class)
                .hasRootCauseInstanceOf(SQLException.class);
    }

    private Category persistCategory() {
        Category category = Category.create(user, "식비", TransactionType.EXPENSE, "#EF4444", 0);
        em.persist(category);
        em.flush();
        return category;
    }
}
