package com.example.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 챗봇 질문.
 *
 * ⚠️ asOf 는 필수다. 서버가 "이번 달"을 now() 로 판정하면
 *    매월 1일 0~9시에 사용자가 한 달 전 답을 받는다 (CLAUDE.md §4).
 *
 * ⚠️ 날짜 포맷 애노테이션을 붙이지 않는다. Jackson 3 의 기본값이 ISO-8601 이라
 *    "2026-09-17" 은 무설정으로 파싱된다 (CLAUDE.md §3).
 */
public record ChatRequest(
        @NotBlank(message = "질문을 입력해 주세요.")
        @Size(max = 200, message = "질문이 너무 깁니다.")
        String message,

        @NotNull(message = "asOf 는 필수입니다.")
        LocalDate asOf
) {
}
