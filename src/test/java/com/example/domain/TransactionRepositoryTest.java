package com.example.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.persistence.EntityManager;

class TransactionRepositoryTest extends RepositoryTestSupport {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private EntityManager em;

    private User user;
    private Category category;

    @BeforeEach
    void setUp() {
        user = User.create("owner@example.com", "{bcrypt}dummy", "테스터");
        em.persist(user);
        category = Category.create(user, "식비", TransactionType.EXPENSE, "#EF4444", 0);
        em.persist(category);
        em.flush();
    }

    @Test
    @DisplayName("Auditing 이 동작해 created_at 과 updated_at 이 채워진다")
    void 생성시각이_자동으로_채워진다() {
        Transaction saved = transactionRepository.save(newTransaction(new BigDecimal("12500")));
        em.flush();

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("BigDecimal 왕복: 12500 을 저장하면 조회 시 scale 이 2 가 되지만 값은 같다")
    void 금액은_scale_이_달라도_값이_같다() {
        Transaction saved = transactionRepository.save(newTransaction(new BigDecimal("12500")));
        em.flush();
        em.clear();   // 영속성 컨텍스트를 비워 DB 에서 다시 읽게 한다

        BigDecimal found = transactionRepository.findById(saved.getId()).orElseThrow().getAmount();

        // DB 는 NUMERIC(15,2) 라 12500.00 으로 돌아온다.
        // equals 는 scale 까지 비교하므로 false 다. 금액 비교는 예외 없이 compareTo 로 한다.
        assertThat(found).isEqualByComparingTo(new BigDecimal("12500"));
        assertThat(found.equals(new BigDecimal("12500"))).isFalse();
        assertThat(found.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("Soft Delete 하면 deleted_at 이 채워지고 소유권 조회에서 빠진다")
    void 삭제하면_조회에서_제외된다() {
        Transaction saved = transactionRepository.save(newTransaction(new BigDecimal("3000")));
        em.flush();

        assertThat(transactionRepository.findByIdAndUserIdAndDeletedAtIsNull(saved.getId(), user.getId()))
                .isPresent();

        saved.softDelete();
        em.flush();

        assertThat(saved.getDeletedAt()).isNotNull();
        assertThat(transactionRepository.findByIdAndUserIdAndDeletedAtIsNull(saved.getId(), user.getId()))
                .isEmpty();
        // 물리 삭제가 아니므로 행 자체는 남아 있어야 한다
        assertThat(transactionRepository.findById(saved.getId())).isPresent();
    }

    @Test
    @DisplayName("타 사용자의 거래는 소유권 조회에서 나오지 않는다")
    void 남의_거래는_조회되지_않는다() {
        Transaction saved = transactionRepository.save(newTransaction(new BigDecimal("3000")));
        User other = User.create("other@example.com", "{bcrypt}dummy", "타인");
        em.persist(other);
        em.flush();

        assertThat(transactionRepository.findByIdAndUserIdAndDeletedAtIsNull(saved.getId(), other.getId()))
                .isEmpty();
    }

    private Transaction newTransaction(BigDecimal amount) {
        return Transaction.create(user, category, TransactionType.EXPENSE,
                amount, LocalDate.of(2026, 9, 14), "스타벅스 강남점", "팀 미팅");
    }
}
