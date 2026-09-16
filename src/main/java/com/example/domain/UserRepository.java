package com.example.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    /** 로그인·인증 경로. deleted_at IS NULL 조건을 항상 건다 */
    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    /** 구글 재로그인 경로. 이메일이 바뀌어도 sub 는 고정이므로 sub 로 찾는다 */
    Optional<User> findByProviderAndProviderIdAndDeletedAtIsNull(AuthProvider provider, String providerId);
}
