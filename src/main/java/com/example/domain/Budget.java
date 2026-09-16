package com.example.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 카테고리별 월 예산.
 *
 * deleted_at 이 없다. 예산은 "지운다"가 아니라 행 자체를 제거해 미설정 상태로 되돌리는 것이 자연스럽다.
 * 물리 삭제를 허용하는 이 프로젝트의 유일한 테이블이며, 집계에서 과거 이력을 참조하지 않으므로 안전하다.
 *
 * year_month 를 CHAR(7) 문자열로 두는 이유는 yyyy-MM 형식이 문자열 정렬 = 시간 정렬이라
 * 범위 조회(BETWEEN '2026-04' AND '2026-09')가 그대로 동작하기 때문이다.
 */
@Entity
@Table(
        name = "budgets",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_budgets_user_category_month",
                columnNames = {"user_id", "category_id", "year_month"}
        )
)
public class Budget extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "year_month", nullable = false, columnDefinition = "char(7)")
    private String yearMonth;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    protected Budget() {
        // JPA 전용
    }

    private Budget(User user, Category category, String yearMonth, BigDecimal amount) {
        this.user = user;
        this.category = category;
        this.yearMonth = yearMonth;
        this.amount = amount;
    }

    public static Budget create(User user, Category category, String yearMonth, BigDecimal amount) {
        return new Budget(user, category, yearMonth, amount);
    }

    public void updateAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Category getCategory() {
        return category;
    }

    public String getYearMonth() {
        return yearMonth;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
