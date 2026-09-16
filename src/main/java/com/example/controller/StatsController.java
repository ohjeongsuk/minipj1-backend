package com.example.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.dto.ApiResponse;
import com.example.dto.MonthlyStatsResponse;
import com.example.dto.RecurringResponse;
import com.example.exception.BusinessException;
import com.example.exception.ErrorCode;
import com.example.service.StatsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * ⚠️ yearMonth 와 asOf 는 필수다. 서버가 "이번 달"과 "오늘"을 판정하지 않는다.
 *    서버는 UTC, 사용자는 KST 라 서버 시각으로 판정하면 매월 1일 0~9시에
 *    사용자가 지난달 대시보드를 보게 된다.
 *    사용자가 자기 asOf 를 조작해도 손해는 자기 데이터의 예측값뿐이라 신뢰해도 안전하다.
 */
@RestController
@RequestMapping("/api/v1/stats")
@Tag(name = "Stats", description = "월 집계 · 예측 · 고정지출 감지")
@SecurityRequirement(name = "bearerAuth")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/monthly")
    @Operation(summary = "월 대시보드 일괄 조회",
            description = "요약·카테고리별·일별·예측·이상치·예산 소진율을 한 번에 반환한다. "
                    + "쪼개면 같은 테이블을 여러 번 스캔하고 화면 안에서 숫자가 어긋난다")
    public ApiResponse<MonthlyStatsResponse> monthly(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "조회 대상 월 (yyyy-MM)", required = true, example = "2026-09")
            @RequestParam String yearMonth,
            @Parameter(description = "사용자의 오늘 (yyyy-MM-dd). 런레이트의 남은 일수 계산에 쓴다",
                    required = true, example = "2026-09-16")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ApiResponse.ok(statsService.monthly(userId, parseYearMonth(yearMonth), asOf));
    }

    @GetMapping("/recurring")
    @Operation(summary = "고정지출 자동 감지",
            description = "asOf 기준 직전 3개월(당월 제외)을 스캔한다. "
                    + "비용이 크고 결과가 월 단위로만 바뀌어 대시보드와 분리했다")
    public ApiResponse<List<RecurringResponse>> recurring(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "사용자의 오늘 (yyyy-MM-dd)", required = true, example = "2026-09-16")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ApiResponse.ok(statsService.recurring(userId, asOf));
    }

    /** 파싱 실패로 500 이 나지 않게 400 INVALID_INPUT 으로 바꾼다 */
    private YearMonth parseYearMonth(String value) {
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "yearMonth 는 yyyy-MM 형식이어야 합니다.");
        }
    }
}
