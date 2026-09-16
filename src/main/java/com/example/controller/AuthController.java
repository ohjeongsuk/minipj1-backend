package com.example.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.dto.ApiResponse;
import com.example.dto.LoginRequest;
import com.example.dto.MeResponse;
import com.example.dto.SignupRequest;
import com.example.dto.TokenResponse;
import com.example.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 로그아웃은 서버 API 를 만들지 않는다.
 * Refresh Token 과 토큰 블랙리스트가 없으므로 프론트에서 토큰 제거 + 캐시 초기화로 처리한다.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "회원가입 · 로그인 · 내 정보")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    @Operation(summary = "회원가입", description = "가입 시 기본 카테고리 9개가 함께 생성된다",
            security = {})   // 인증 불필요. 전역 SecurityRequirement 를 이 엔드포인트에서만 해제한다
    public ResponseEntity<ApiResponse<MeResponse>> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(authService.signup(request)));
    }

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "24시간 유효한 JWT 를 반환한다", security = {})
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회")
    @SecurityRequirement(name = "bearerAuth")
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(authService.me(userId));
    }
}
