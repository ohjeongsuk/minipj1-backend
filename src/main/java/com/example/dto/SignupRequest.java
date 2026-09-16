package com.example.dto;

import com.example.dto.validation.MaxByteLength;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 최소 길이는 문자 수, 최대 길이는 바이트로 검증한다.
 * 위반 시 400 INVALID_INPUT + 필드 메시지로 나가야 한다. 500 이 나가면 안 된다.
 */
public record SignupRequest(

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 6, message = "비밀번호는 6자 이상이어야 합니다.")
        @MaxByteLength(value = 72, message = "비밀번호가 너무 깁니다. (한글은 1자가 3바이트로 계산됩니다)")
        String password,

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = 1, max = 50, message = "닉네임은 1~50자여야 합니다.")
        String nickname
) {
}
