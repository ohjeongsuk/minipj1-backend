package com.example.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 거래 내역.
 *
 * amount 는 항상 양수다. 부호는 type 으로만 표현한다.
 * 음수를 허용하면 "지출 -5000"이 환불인지 입력 실수인지 알 수 없고 집계가 이중 의미를 갖는다.
 * 환불은 반대 type 의 거래로 기록한다. DB 제약은 db/schema-extra.sql 에 있다.
 *
 * txn_date 는 LocalDate(시각 없음)라 타임존 영향을 받지 않는다.
 */
@Entity
@Table(
        name = "transactions",
        indexes = {
                // 목록·월별 집계의 주 경로
                @Index(name = "idx_txn_user_date", columnList = "user_id, txn_date DESC, deleted_at"),
                // 카테고리별 집계
                @Index(name = "idx_txn_user_category", columnList = "user_id, category_id, deleted_at")
        }
)
public class Transaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate;

    /** 거래처/상호. 고정지출 자동 감지의 기준이 된다 */
    @Column(length = 100)
    private String merchant;

    /** 평문이다. HTML 이 아니다 */
    @Column(length = 500)
    private String memo;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected Transaction() {
        // JPA 전용
    }

    private Transaction(User user, Category category, TransactionType type,
                        BigDecimal amount, LocalDate txnDate, String merchant, String memo) {
        this.user = user;
        this.category = category;
        this.type = type;
        this.amount = amount;
        this.txnDate = txnDate;
        this.merchant = merchant;
        this.memo = memo;
    }

    public static Transaction create(User user, Category category, TransactionType type,
                                     BigDecimal amount, LocalDate txnDate, String merchant, String memo) {
        return new Transaction(user, category, type, amount, txnDate, merchant, memo);
    }

    /** PUT 은 부분 수정이 아니라 전체 교체다. merchant·memo 가 null 이면 값 삭제로 취급한다 */
    public void update(Category category, TransactionType type, BigDecimal amount,
                       LocalDate txnDate, String merchant, String memo) {
        this.category = category;
        this.type = type;
        this.amount = amount;
        this.txnDate = txnDate;
        this.merchant = merchant;
        this.memo = memo;
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
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

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getTxnDate() {
        return txnDate;
    }

    public String getMerchant() {
        return merchant;
    }

    public String getMemo() {
        return memo;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }
}
