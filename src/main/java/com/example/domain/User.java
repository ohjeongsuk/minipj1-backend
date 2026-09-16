package com.example.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * provider 는 인증 수단이다. 기본값이 LOCAL 이라 기존 계정은 전부 LOCAL 로 남는다.
 * provider_id 는 구글의 sub 값이며 로컬 계정에서는 NULL 이다.
 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /**
     * BCrypt 해시. 평문을 담지 않는다.
     *
     * ⚠️ 구글 계정은 NULL 이다. 랜덤 해시를 채우면 "비밀번호가 있는 계정"처럼 보여
     *    로그인 경로가 헷갈린다. 비밀번호 로그인 가능 여부는 canLoginWithPassword() 로 판정한다.
     */
    @Column(length = 255)
    private String password;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider = AuthProvider.LOCAL;

    /** 구글의 sub. 로컬 계정에서는 NULL 이다 */
    @Column(name = "provider_id", length = 255)
    private String providerId;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected User() {
        // JPA 전용
    }

    private User(String email, String password, String nickname,
                 AuthProvider provider, String providerId) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.provider = provider;
        this.providerId = providerId;
    }

    public static User create(String email, String encodedPassword, String nickname) {
        return new User(email, encodedPassword, nickname, AuthProvider.LOCAL, null);
    }

    /** 구글로 처음 로그인한 사용자. 비밀번호가 없다 */
    public static User google(String email, String nickname, String providerId) {
        return new User(email, null, nickname, AuthProvider.GOOGLE, providerId);
    }

    /**
     * 비밀번호로 로그인할 수 있는 계정인지.
     * 구글 계정에 대고 matches(raw, null) 을 부르면 그대로 터진다.
     */
    public boolean canLoginWithPassword() {
        return provider == AuthProvider.LOCAL && password != null;
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

    public AuthProvider getProvider() {
        return provider;
    }

    public String getProviderId() {
        return providerId;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }
}
