package com.example.dto;

import com.example.domain.User;

/**
 * 내 정보. email 은 응답에 포함하지만 화면에는 표시하지 않는다(AUTH-08).
 * password 는 절대 담지 않는다.
 */
public record MeResponse(Long id, String email, String nickname) {

    public static MeResponse from(User user) {
        return new MeResponse(user.getId(), user.getEmail(), user.getNickname());
    }
}
