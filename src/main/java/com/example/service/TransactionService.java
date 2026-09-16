package com.example.service;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.domain.Category;
import com.example.domain.CategoryRepository;
import com.example.domain.Transaction;
import com.example.domain.TransactionRepository;
import com.example.domain.TransactionType;
import com.example.domain.User;
import com.example.domain.UserRepository;
import com.example.dto.PageResponse;
import com.example.dto.TransactionCreateRequest;
import com.example.dto.TransactionResponse;
import com.example.dto.TransactionUpdateRequest;
import com.example.exception.BusinessException;
import com.example.exception.ErrorCode;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    public TransactionService(TransactionRepository transactionRepository,
                              CategoryRepository categoryRepository,
                              UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> search(Long userId,
                                                    LocalDate from,
                                                    LocalDate to,
                                                    TransactionType type,
                                                    Long categoryId,
                                                    String keyword,
                                                    Integer page,
                                                    Integer size,
                                                    String sort) {
        PageRequest pageRequest = TransactionSort.toPageRequest(page, size, sort);
        Page<Transaction> result = transactionRepository.search(
                userId, from, to,
                type == null ? null : type.name(),
                categoryId,
                toLikePattern(keyword),
                pageRequest);
        return PageResponse.from(result, TransactionResponse::from);
    }

    @Transactional(readOnly = true)
    public TransactionResponse findOne(Long userId, Long transactionId) {
        return TransactionResponse.from(findOwnedDetail(userId, transactionId));
    }

    @Transactional
    public TransactionResponse create(Long userId, TransactionCreateRequest request) {
        Category category = findOwnedCategory(userId, request.categoryId());
        validateTypeMatches(request.type(), category);

        User user = userRepository.getReferenceById(userId);
        Transaction transaction = transactionRepository.save(Transaction.create(
                user, category, request.type(), request.amount(),
                request.txnDate(), request.merchant(), request.memo()));
        return TransactionResponse.from(transaction);
    }

    /** PUT 은 전체 교체다. merchant·memo 누락은 값 삭제로 취급한다 */
    @Transactional
    public TransactionResponse update(Long userId, Long transactionId, TransactionUpdateRequest request) {
        Transaction transaction = findOwnedDetail(userId, transactionId);
        Category category = findOwnedCategory(userId, request.categoryId());
        validateTypeMatches(request.type(), category);

        transaction.update(category, request.type(), request.amount(),
                request.txnDate(), request.merchant(), request.memo());
        return TransactionResponse.from(transaction);
    }

    @Transactional
    public void delete(Long userId, Long transactionId) {
        findOwnedDetail(userId, transactionId).softDelete();
    }

    /**
     * 지출 카테고리에 수입 거래가 들어가면 카테고리별 집계가 조용히 깨진다.
     * 스키마로는 막을 수 없으므로 여기서 검증한다.
     */
    private void validateTypeMatches(TransactionType type, Category category) {
        if (category.getType() != type) {
            throw new BusinessException(ErrorCode.CATEGORY_TYPE_MISMATCH);
        }
    }

    /** 새 거래에는 삭제된 카테고리를 고를 수 없다 */
    private Category findOwnedCategory(Long userId, Long categoryId) {
        return categoryRepository.findByIdAndUserIdAndDeletedAtIsNull(categoryId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    /** 소유자가 아니거나 없으면 404. 존재 여부를 노출하지 않기 위해 403 이 아니다 */
    private Transaction findOwnedDetail(Long userId, Long transactionId) {
        return transactionRepository.findDetail(transactionId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND));
    }

    /** 대소문자를 무시하려면 양쪽을 모두 소문자로 맞춰야 한다 */
    private String toLikePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return "%" + keyword.trim().toLowerCase() + "%";
    }
}
