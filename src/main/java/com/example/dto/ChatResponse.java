package com.example.dto;

import java.util.List;

import com.example.service.chat.IntentType;

/**
 * 챗봇 응답.
 *
 * @param yearMonth    파서가 해석한 대상 월. 화면이 "이렇게 알아들었습니다" 를 보여줄 수 있어야 한다.
 *                     규칙 기반 파서는 오해할 수 있고, 오해했을 때 틀린 답을 맞는 답처럼
 *                     보여주는 것이 가장 나쁜 실패다
 * @param transactions RECENT_TRANSACTIONS 일 때만 채운다. 기존 TransactionResponse 를 그대로 쓴다
 * @param suggestions  UNKNOWN 일 때만 예시 질문. 그 외에는 빈 배열
 */
public record ChatResponse(
        IntentType intent,
        String answer,
        String yearMonth,
        List<TransactionResponse> transactions,
        List<String> suggestions
) {
}
