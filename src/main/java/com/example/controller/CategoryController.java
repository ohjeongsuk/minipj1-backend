package com.example.controller;

import java.util.List;

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
import com.example.dto.CategoryCreateRequest;
import com.example.dto.CategoryResponse;
import com.example.dto.CategoryUpdateRequest;
import com.example.service.CategoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/categories")
@Tag(name = "Category", description = "카테고리 관리")
@SecurityRequirement(name = "bearerAuth")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @Operation(summary = "카테고리 목록", description = "sortOrder ASC, id ASC 고정. 삭제된 카테고리는 제외된다")
    public ApiResponse<List<CategoryResponse>> findAll(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) TransactionType type) {
        return ApiResponse.ok(categoryService.findAll(userId, type));
    }

    @PostMapping
    @Operation(summary = "카테고리 생성")
    public ResponseEntity<ApiResponse<CategoryResponse>> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CategoryCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(categoryService.create(userId, request)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "카테고리 수정", description = "구분(type)은 생성 후 변경할 수 없어 요청 필드에 없다")
    public ApiResponse<CategoryResponse> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody CategoryUpdateRequest request) {
        return ApiResponse.ok(categoryService.update(userId, id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "카테고리 삭제", description = "Soft Delete. 과거 거래는 그대로 유지된다")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        categoryService.delete(userId, id);
        return ApiResponse.ok();
    }
}
