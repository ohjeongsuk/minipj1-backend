package com.example.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.domain.Category;
import com.example.domain.CategoryRepository;
import com.example.domain.TransactionType;
import com.example.domain.User;
import com.example.domain.UserRepository;
import com.example.dto.CategoryCreateRequest;
import com.example.dto.CategoryResponse;
import com.example.dto.CategoryUpdateRequest;
import com.example.exception.BusinessException;
import com.example.exception.ErrorCode;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    public CategoryService(CategoryRepository categoryRepository, UserRepository userRepository) {
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
    }

    /**
     * 선택 UI 용 목록. 삭제된 카테고리는 새 거래에 고를 수 없어야 하므로 제외한다.
     * 페이지네이션은 두지 않는다(개수가 수십 개를 넘지 않는다).
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll(Long userId, TransactionType type) {
        List<Category> categories = (type == null)
                ? categoryRepository.findByUserIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(userId)
                : categoryRepository.findByUserIdAndTypeAndDeletedAtIsNullOrderBySortOrderAscIdAsc(userId, type);
        return categories.stream().map(CategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse create(Long userId, CategoryCreateRequest request) {
        // 사전 확인이다. 최종 방어선은 부분 유니크 인덱스(db/schema-extra.sql)
        if (categoryRepository.existsByUserIdAndNameAndTypeAndDeletedAtIsNull(
                userId, request.name(), request.type())) {
            throw new BusinessException(ErrorCode.CATEGORY_DUPLICATED);
        }

        User user = userRepository.getReferenceById(userId);
        Category category = categoryRepository.save(Category.create(
                user, request.name(), request.type(), request.color(), request.sortOrderOrZero()));
        return CategoryResponse.from(category);
    }

    /** type 은 바꾸지 않는다. 요청 DTO 에 아예 필드가 없다 */
    @Transactional
    public CategoryResponse update(Long userId, Long categoryId, CategoryUpdateRequest request) {
        Category category = findOwned(userId, categoryId);

        // 이름이 바뀌는 경우에만 중복을 확인한다. 자기 자신과 부딪히면 안 된다
        if (!category.getName().equals(request.name())
                && categoryRepository.existsByUserIdAndNameAndTypeAndDeletedAtIsNull(
                        userId, request.name(), category.getType())) {
            throw new BusinessException(ErrorCode.CATEGORY_DUPLICATED);
        }

        category.update(request.name(), request.color(), request.sortOrderOrZero());
        return CategoryResponse.from(category);
    }

    /**
     * Soft Delete.
     * 거래가 있는 카테고리를 물리 삭제하면 과거 내역이 조인에서 탈락하므로 항상 Soft Delete 다.
     */
    @Transactional
    public void delete(Long userId, Long categoryId) {
        findOwned(userId, categoryId).softDelete();
    }

    /** 소유자가 아니거나 없으면 404. 존재 여부를 노출하지 않기 위해 403 이 아니다 */
    private Category findOwned(Long userId, Long categoryId) {
        return categoryRepository.findByIdAndUserIdAndDeletedAtIsNull(categoryId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
    }
}
