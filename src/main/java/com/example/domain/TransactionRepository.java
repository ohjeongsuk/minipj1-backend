package com.example.domain;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /** 소유권 검증용. 불일치 시 서비스가 404 로 응답한다 */
    Optional<Transaction> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    /** 거래가 하나라도 있는 카테고리는 물리 삭제하지 않는다는 규칙의 근거 */
    boolean existsByCategoryIdAndDeletedAtIsNull(Long categoryId);

    /** 단건 조회에도 fetch join 을 쓴다. 응답에 카테고리 이름·색이 들어가기 때문이다 */
    @Query("""
            select t from Transaction t
            join fetch t.category
            where t.id = :id and t.user.id = :userId and t.deletedAt is null
            """)
    Optional<Transaction> findDetail(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 목록 조회. 필터는 전부 선택이며 null 이면 조건에서 빠진다.
     *
     * ⚠️ (:param is null or ...) 패턴을 쓰지 않는다. PostgreSQL 에서 터진다.
     *    그 형태로 쓰면 파라미터가 IS NULL 에만 등장해 타입을 추론할 단서가 없고,
     *    "$2 매개 변수의 자료형을 알 수 없습니다" 오류가 난다.
     *    더 나쁜 건 즉시 드러나지 않는다는 점이다 — PgJDBC 는 같은 SQL 이
     *    prepareThreshold(기본 5회) 실행될 때까지 클라이언트에서 리터럴로 치환하다가
     *    그 뒤 서버 측 prepared statement 로 전환하면서 비로소 실패한다.
     *    개발 중 몇 번 호출로는 재현되지 않고 운영에서 갑자기 500 이 난다.
     *
     *    대신 coalesce 로 타입 단서를 준다.
     *    coalesce(:from, t.txnDate) 는 :from 이 null 이면 항상 참이 되면서
     *    형제 인자에서 date 타입이 추론된다.
     *
     * ⚠️ join fetch t.category 에 deleted_at IS NULL 을 걸지 않는다.
     *    걸면 삭제된 카테고리를 쓰던 과거 거래가 조인에서 탈락해 목록에서 통째로 사라진다.
     *
     * ⚠️ category 는 @ManyToOne 이라 fetch join 과 페이징을 함께 써도 SQL 레벨에서 처리된다.
     *    컬렉션(@OneToMany)을 fetch join 하면 Hibernate 가 전체를 메모리에 올려 페이징한다.
     *
     * keyword 는 호출자가 소문자 + 양쪽 % 를 붙여서 넘긴다. null 이면 '%' 로 대체돼 전체가 통과한다.
     * merchant·memo 가 null 인 행도 걸러지지 않도록 coalesce 로 빈 문자열을 씌운다.
     */
    @Query(value = """
            select t from Transaction t
            join fetch t.category c
            where t.user.id = :userId
              and t.deletedAt is null
              and t.txnDate >= coalesce(:from, t.txnDate)
              and t.txnDate <= coalesce(:to, t.txnDate)
              and cast(t.type as string) = coalesce(:typeName, cast(t.type as string))
              and c.id = coalesce(:categoryId, c.id)
              and (lower(coalesce(t.merchant, '')) like coalesce(:keyword, '%')
                   or lower(coalesce(t.memo, '')) like coalesce(:keyword, '%'))
            """,
            countQuery = """
            select count(t) from Transaction t
            where t.user.id = :userId
              and t.deletedAt is null
              and t.txnDate >= coalesce(:from, t.txnDate)
              and t.txnDate <= coalesce(:to, t.txnDate)
              and cast(t.type as string) = coalesce(:typeName, cast(t.type as string))
              and t.category.id = coalesce(:categoryId, t.category.id)
              and (lower(coalesce(t.merchant, '')) like coalesce(:keyword, '%')
                   or lower(coalesce(t.memo, '')) like coalesce(:keyword, '%'))
            """)
    Page<Transaction> search(@Param("userId") Long userId,
                            @Param("from") LocalDate from,
                            @Param("to") LocalDate to,
                            @Param("typeName") String typeName,
                            @Param("categoryId") Long categoryId,
                            @Param("keyword") String keyword,
                            Pageable pageable);
}
