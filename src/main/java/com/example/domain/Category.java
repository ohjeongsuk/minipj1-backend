package com.example.domain;

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
 * 카테고리.
 *
 * @SQLRestriction("deleted_at IS NULL") 을 붙이지 않는다.
 * 그 애노테이션은 조인을 포함한 모든 조회에 전역 적용되어,
 * 삭제된 카테고리를 쓰던 과거 거래가 목록에서 통째로 사라진다.
 * 삭제 필터링은 용도별로 Repository 쿼리에서 직접 건다.
 *
 * 이름 중복 방지는 부분 유니크 인덱스가 담당한다(db/schema-extra.sql).
 * 일반 UNIQUE 로는 "삭제한 이름을 다시 쓸 수 있다"(CAT-04)를 만족할 수 없다.
 */
@Entity
@Table(
        name = "categories",
        indexes = @Index(name = "idx_categories_user_deleted", columnList = "user_id, deleted_at")
)
public class Category extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 30)
    private String name;

    /** 생성 후 변경할 수 없다. 바꾸면 이미 쌓인 거래의 집계가 뒤집힌다 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    /** #RRGGBB 형식. 검증은 DTO 와 프론트 양쪽에서 한다 */
    @Column(nullable = false, columnDefinition = "char(7)")
    private String color;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected Category() {
        // JPA 전용
    }

    private Category(User user, String name, TransactionType type, String color, int sortOrder) {
        this.user = user;
        this.name = name;
        this.type = type;
        this.color = color;
        this.sortOrder = sortOrder;
    }

    public static Category create(User user, String name, TransactionType type, String color, int sortOrder) {
        return new Category(user, name, type, color, sortOrder);
    }

    /** type 은 의도적으로 제외한다 */
    public void update(String name, String color, int sortOrder) {
        this.name = name;
        this.color = color;
        this.sortOrder = sortOrder;
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

    public String getName() {
        return name;
    }

    public TransactionType getType() {
        return type;
    }

    public String getColor() {
        return color;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }
}
