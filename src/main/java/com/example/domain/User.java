package com.example.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 회원.
 *
 * deleted_at 은 이번 범위에서 항상 NULL 이다(회원 탈퇴가 비목표라 값을 채우는 경로가 없다).
 * 다만 스키마와 조회 조건은 유지해, 향후 탈퇴 기능을 추가할 때 구조를 바꾸지 않는다.
 *
 * provider / provider_id 컬럼은 만들지 않는다. 구글 로그인이 범위 밖이라 항상 LOCAL/NULL 인 컬럼이 된다.
 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /** BCrypt 해시. 평문을 담지 않는다 */
    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected User() {
        // JPA 전용
    }

    private User(String email, String password, String nickname) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
    }

    public static User create(String email, String encodedPassword, String nickname) {
        return new User(email, encodedPassword, nickname);
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
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

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getNickname() {
        return nickname;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }
}
