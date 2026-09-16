package com.example.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.dto.ApiResponse;
import com.example.dto.ImportResultResponse;
import com.example.exception.BusinessException;
import com.example.exception.ErrorCode;
import com.example.service.DataService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/data")
@Tag(name = "Data", description = "CSV 내보내기 / 가져오기")
@SecurityRequirement(name = "bearerAuth")
public class DataController {

    private final DataService dataService;

    public DataController(DataService dataService) {
        this.dataService = dataService;
    }

    /**
     * ⚠️ 이 엔드포인트만 ApiResponse 봉투를 쓰지 않는다. CSV 바이트를 직접 반환한다.
     *
     * Content-Disposition 을 프론트가 읽으려면 CORS 의 exposedHeaders 에 있어야 한다
     * (CorsConfig 에 추가해 두었다). 빠뜨리면 브라우저가 헤더를 숨겨 파일명이 download 가 된다.
     */
    @GetMapping("/export")
    @Operation(summary = "거래 내역 CSV 다운로드",
            description = "ApiResponse 봉투를 쓰지 않는다. 본문 맨 앞에 UTF-8 BOM 이 붙는다")
    public ResponseEntity<byte[]> export(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        byte[] csv = dataService.export(userId, from, to);
        String fileName = dataService.exportFileName(from, to);

        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(fileName, StandardCharsets.UTF_8)
                                .build().toString())
                .body(csv);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "CSV 가져오기",
            description = "부분 성공을 허용한다. 실패한 행만 건너뛰고 사유를 행 번호와 함께 반환한다. "
                    + "같은 파일을 두 번 올리면 거래가 두 번 등록된다(중복 검사를 하지 않는다)")
    public ApiResponse<ImportResultResponse> importCsv(
            @AuthenticationPrincipal Long userId,
            @RequestParam("file") MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_CSV, "업로드할 파일이 없습니다.");
        }
        try {
            return ApiResponse.ok(dataService.importCsv(userId, file.getBytes()));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_CSV, "파일을 읽을 수 없습니다.");
        }
    }
}
