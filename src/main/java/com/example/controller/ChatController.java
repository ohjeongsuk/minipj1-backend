package com.example.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.dto.ApiResponse;
import com.example.dto.ChatRequest;
import com.example.dto.ChatResponse;
import com.example.service.chat.ChatService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 조회 전용 챗봇.
 *
 * ⚠️ 의도를 못 찾아도 4xx 가 아니라 200 + UNKNOWN 이다.
 *    사용자가 예상 밖으로 물어본 것은 클라이언트 잘못도 서버 오류도 아니다.
 *    4xx 로 만들면 프론트의 에러 경로를 타서 예시 질문을 보여줄 수 없다.
 *
 * ⚠️ 이 엔드포인트는 데이터를 바꾸지 않는다. 자연어 오해로 거래가 바뀌면
 *    되돌릴 방법이 없다.
 *
 * ⚠️ asOf 는 요청 바디로 받는다. 서버가 "오늘"을 판정하지 않는다 (CLAUDE.md §4).
 */
@RestController
@RequestMapping("/api/v1/chat")
@Tag(name = "Chat", description = "자연어 조회")
@SecurityRequirement(name = "bearerAuth")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    @Operation(summary = "자연어 질문",
            description = "월 요약·카테고리별 금액·최근 내역·예산 소진율을 자연어로 조회한다. "
                    + "의도를 찾지 못하면 200 과 함께 UNKNOWN 및 예시 질문을 돌려준다")
    public ApiResponse<ChatResponse> ask(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ChatRequest request) {
        return ApiResponse.ok(chatService.ask(userId, request.message(), request.asOf()));
    }
}
