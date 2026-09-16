package com.example.domain;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;

/**
 * 생성·수정 시각 자동 관리.
 *
 * deletedAt 은 여기에 두지 않는다. Budget 이 이 클래스를 상속하는데
 * budgets 테이블에는 deleted_at 컬럼이 없어야 하기 때문이다(물리 삭제를 허용하는 유일한 테이블).
 *
 * 동작하려면 메인 애플리케이션 클래스에 @EnableJpaAuditing 이 있어야 한다.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
