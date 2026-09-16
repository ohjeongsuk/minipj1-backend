package com.example.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.dto.ApiResponse;
import com.example.dto.BudgetResponse;
import com.example.dto.BudgetUpsertRequest;
import com.example.service.BudgetService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * POST 와 DELETE 를 두지 않는다.
 * 예산 화면은 "카테고리 목록에 금액을 채워 한 번에 저장"하는 형태라 개별 경로가 필요 없다.
 *
 * 소진율은 이 API 가 아니라 stats/monthly 의 budgets 배열에서 내려준다.
 * 같은 화면에서 지출과 함께 보이기 때문이다.
 */
@RestController
@RequestMapping("/api/v1/budgets")
@Tag(name = "Budget", description = "카테고리별 월 예산")
@SecurityRequirement(name = "bearerAuth")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping
    @Operation(summary = "월 예산 목록",
            description = "지출 카테고리 전체를 반환하고 설정된 것만 금액을 채운다. 미설정은 amount 가 null 이다")
    public ApiResponse<List<BudgetResponse>> findAll(
            @AuthenticationPrincipal Long userId,
            @RequestParam String yearMonth) {
        return ApiResponse.ok(budgetService.findAll(userId, yearMonth));
    }

    @PutMapping
    @Operation(summary = "월 예산 일괄 저장 (upsert)",
            description = "amount 가 0 또는 null 인 항목은 행을 제거한다")
    public ApiResponse<List<BudgetResponse>> upsert(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody BudgetUpsertRequest request) {
        return ApiResponse.ok(budgetService.upsert(userId, request));
    }
}
