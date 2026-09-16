package com.example.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.domain.TransactionType;
import com.example.dto.ApiResponse;
import com.example.dto.PageResponse;
import com.example.dto.TransactionCreateRequest;
import com.example.dto.TransactionResponse;
import com.example.dto.TransactionUpdateRequest;
import com.example.service.TransactionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transaction", description = "거래 내역")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * ⚠️ Pageable 을 파라미터로 받지 않는다.
     *    받으면 ?sort=foo,desc 의 임의 문자열이 그대로 Sort 에 담겨 500 이 난다.
     *    page·size·sort 를 직접 받아 TransactionSort 에서 화이트리스트로 거른다.
     */
    @GetMapping
    @Operation(summary = "거래 목록",
            description = "정렬은 txnDate·amount·createdAt 만 허용하며 항상 id DESC 가 2차 키로 붙는다")
    public ApiResponse<PageResponse<TransactionResponse>> search(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "거래처 + 메모 부분 일치, 대소문자 무시")
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.ok(transactionService.search(
                userId, from, to, type, categoryId, keyword, page, size, sort));
    }

    @PostMapping
    @Operation(summary = "거래 생성")
    public ResponseEntity<ApiResponse<TransactionResponse>> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TransactionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(transactionService.create(userId, request)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "거래 단건 조회")
    public ApiResponse<TransactionResponse> findOne(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        return ApiResponse.ok(transactionService.findOne(userId, id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "거래 수정", description = "부분 수정이 아니라 전체 교체다. merchant·memo 누락은 값 삭제다")
    public ApiResponse<TransactionResponse> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody TransactionUpdateRequest request) {
        return ApiResponse.ok(transactionService.update(userId, id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "거래 삭제", description = "Soft Delete")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        transactionService.delete(userId, id);
        return ApiResponse.ok();
    }
}
