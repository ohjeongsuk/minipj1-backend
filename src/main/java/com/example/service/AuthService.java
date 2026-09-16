package com.example.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.config.JwtTokenProvider;
import com.example.domain.Category;
import com.example.domain.CategoryRepository;
import com.example.domain.User;
import com.example.domain.UserRepository;
import com.example.dto.LoginRequest;
import com.example.dto.MeResponse;
import com.example.dto.SignupRequest;
import com.example.dto.TokenResponse;
import com.example.exception.BusinessException;
import com.example.exception.ErrorCode;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final long expirationMillis;

    public AuthService(UserRepository userRepository,
                       CategoryRepository categoryRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       @Value("${app.jwt.expiration}") long expirationMillis) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.expirationMillis = expirationMillis;
    }

    /** 회원가입. 같은 트랜잭션에서 기본 카테고리 9개를 함께 만든다 */
    @Transactional
    public MeResponse signup(SignupRequest request) {
        if (userRepository.existsByEmailAndDeletedAtIsNull(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }

        User user = userRepository.save(User.create(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.nickname()));

        createDefaultCategories(user);

        return MeResponse.from(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        // 미가입 이메일과 비밀번호 오류의 응답을 동일하게 만든다(계정 존재 여부 노출 방지)
        User user = userRepository.findByEmailAndDeletedAtIsNull(request.email())
                .filter(found -> passwordEncoder.matches(request.password(), found.getPassword()))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."));

        return TokenResponse.bearer(
                tokenProvider.createToken(user.getId(), user.getEmail()), expirationMillis);
    }

    @Transactional(readOnly = true)
    public MeResponse me(Long userId) {
        // 토큰은 유효한데 사용자가 조회되지 않으면 404 가 아니라 401 이다.
        // 프론트가 401 을 자동 로그아웃으로 처리하므로 일관된다.
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .map(MeResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    private void createDefaultCategories(User user) {
        List<Category> categories = java.util.stream.IntStream.range(0, DefaultCategories.ALL.size())
                .mapToObj(index -> {
                    DefaultCategories.Seed seed = DefaultCategories.ALL.get(index);
                    return Category.create(user, seed.name(), seed.type(), seed.color(), index);
                })
                .toList();
        categoryRepository.saveAll(categories);
    }
}
