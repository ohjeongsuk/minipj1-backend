package com.example.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.example.domain.Category;
import com.example.domain.Transaction;
import com.example.domain.TransactionType;

/**
 * 사용자 정보는 넣지 않는다. 본인 데이터만 조회하므로 불필요하다.
 *
 * 반면 카테고리는 목록 화면이 이름과 색을 그리므로 함께 담는다.
 * 그래서 목록 쿼리에 join fetch 가 필요하다(빼는 게 아니라 fetch join 으로 해결한다).
 */
public record TransactionResponse(
        Long id,
        TransactionType type,
        BigDecimal amount,
        LocalDate txnDate,
        String merchant,
        String memo,
        CategorySummary category
) {

    /** 목록에 필요한 최소 정보. deleted 는 화면에서 "(삭제됨)"을 붙이는 데 쓴다 */
    public record CategorySummary(Long id, String name, String color, boolean deleted) {

        static CategorySummary from(Category category) {
            return new CategorySummary(
                    category.getId(), category.getName(), category.getColor(), category.isDeleted());
        }
    }

    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getTxnDate(),
                transaction.getMerchant(),
                transaction.getMemo(),
                CategorySummary.from(transaction.getCategory()));
    }
}
