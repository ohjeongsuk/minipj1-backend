package com.example.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.config.JwtTokenProvider;
import com.example.domain.AuthProvider;
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
        /*
         * 미가입 이메일과 비밀번호 오류의 응답을 동일하게 만든다(계정 존재 여부 노출 방지).
         *
         * ⚠️ canLoginWithPassword() 를 먼저 건다. 구글 계정은 password 가 NULL 이라
         *    matches(raw, null) 을 부르면 NPE 로 500 이 나간다.
         *    구글 계정임을 알려주지 않고 같은 401 문구로 응답한다.
         */
        User user = userRepository.findByEmailAndDeletedAtIsNull(request.email())
                .filter(User::canLoginWithPassword)
                .filter(found -> passwordEncoder.matches(request.password(), found.getPassword()))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."));

        return TokenResponse.bearer(
                tokenProvider.createToken(user.getId(), user.getEmail()), expirationMillis);
    }

    /**
     * 구글 로그인. 처음이면 그 자리에서 가입 처리한다.
     *
     * ⚠️ sub 로 먼저 찾는다. 사용자가 구글 계정의 이메일을 바꿔도 sub 는 고정이므로,
     *    이메일로만 찾으면 같은 사람이 새 계정으로 갈라진다.
     *
     * ⚠️ 같은 이메일의 로컬 계정이 있으면 거부한다. 자동 연동하지 않는다 —
     *    구글 이메일 소유만으로 기존 비밀번호 계정을 차지하는 경로가 되기 때문이다.
     */
    @Transactional
    public User loginOrRegisterGoogle(String email, String nickname, String providerId) {
        Optional<User> bySub = userRepository
                .findByProviderAndProviderIdAndDeletedAtIsNull(AuthProvider.GOOGLE, providerId);
        if (bySub.isPresent()) {
            return bySub.get();
        }

        Optional<User> byEmail = userRepository.findByEmailAndDeletedAtIsNull(email);
        if (byEmail.isPresent()) {
            throw new BusinessException(ErrorCode.EMAIL_CONFLICT);
        }

        User user = userRepository.save(User.google(email, nickname, providerId));
        // 일반 가입과 똑같이 기본 카테고리 9개를 같은 트랜잭션에서 만든다 (AUTH-05)
        createDefaultCategories(user);
        return user;
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
