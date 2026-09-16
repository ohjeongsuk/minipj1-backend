package com.example.config;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

/**
 * Access Token 발급·검증. Refresh Token 은 이번 범위에 없다.
 *
 * ⚠️ signWith 에 알고리즘을 반드시 넘긴다.
 *    인자 없는 signWith(key) 를 쓰면 jjwt 가 키 길이로 알고리즘을 추론한다
 *    (32~47B→HS256, 48~63B→HS384, 64B+→HS512).
 *    그러면 "HS256 으로 고정한다"는 규칙이 코드가 아니라 JWT_SECRET 의 길이에 좌우된다.
 *    어디서도 오류가 나지 않고 토큰 검증도 통과하므로, 헤더를 디코드해야만 드러난다.
 *
 * ⚠️ Keys.hmacShaKeyFor(bytes) 는 바이트 길이로 JCA 알고리즘명을 정한다(53바이트면 HmacSHA384).
 *    거기에 Jwts.SIG.HS256 을 명시하면 키 알고리즘명 검증에 걸린다.
 *    그래서 SecretKeySpec 으로 HmacSHA256 키를 직접 만든다. 시크릿 길이와 무관하게 HS256 이 보장된다.
 */
@Component
public class JwtTokenProvider {

    /** 토큰에 이메일을 함께 담아, 사용처에서 추가 조회 없이 쓸 수 있게 한다 */
    private static final String CLAIM_EMAIL = "email";

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long expirationMillis;

    public JwtTokenProvider(@Value("${app.jwt.secret}") String secret,
                            @Value("${app.jwt.expiration}") long expirationMillis) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET 은 raw UTF-8 기준 32바이트 이상이어야 합니다. 현재: " + keyBytes.length + "바이트");
        }
        this.key = new SecretKeySpec(keyBytes, "HmacSHA256");
        this.expirationMillis = expirationMillis;
    }

    /** sub 에는 이메일이 아니라 user.id 를 담는다. 인증 필터에서 PK 조회로 끝난다 */
    public String createToken(Long userId, String email) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_EMAIL, email)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMillis))
                .signWith(key, Jwts.SIG.HS256)   // 알고리즘을 반드시 명시한다
                .compact();
    }

    /** 서명·만료가 유효하면 user.id 를 반환하고, 아니면 null 을 반환한다 */
    public Long parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            // 서명 불일치·만료·형식 오류를 모두 "인증 실패"로 취급한다.
            // 사유를 클라이언트에 구분해 알리지 않는다.
            return null;
        }
    }
}
