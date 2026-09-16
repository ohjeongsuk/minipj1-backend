package com.example.service;

import java.util.List;

import com.example.domain.TransactionType;

/**
 * 가입 시 자동 생성하는 기본 카테고리 9개 (EXPENSE 7 + INCOME 2).
 * 카테고리가 하나도 없으면 거래를 한 건도 등록할 수 없어 가입 직후 화면이 막힌다.
 *
 * is_default 같은 플래그를 두지 않는다. 플래그를 만들면
 * "기본 카테고리는 삭제 못 함" 같은 규칙이 따라붙고, 그건 아무도 요청하지 않은 제약이다.
 * 사용자는 이후 자유롭게 수정·삭제·추가할 수 있다.
 */
final class DefaultCategories {

    record Seed(TransactionType type, String name, String color) {}

    static final List<Seed> ALL = List.of(
            new Seed(TransactionType.EXPENSE, "식비", "#EF4444"),
            new Seed(TransactionType.EXPENSE, "교통", "#F59E0B"),
            new Seed(TransactionType.EXPENSE, "주거/통신", "#6366F1"),
            new Seed(TransactionType.EXPENSE, "생활용품", "#10B981"),
            new Seed(TransactionType.EXPENSE, "문화/여가", "#EC4899"),
            new Seed(TransactionType.EXPENSE, "의료/건강", "#14B8A6"),
            new Seed(TransactionType.EXPENSE, "기타", "#737373"),
            new Seed(TransactionType.INCOME, "급여", "#4F46E5"),
            new Seed(TransactionType.INCOME, "기타수입", "#737373")
    );

    private DefaultCategories() {
    }
}
