package com.example.domain;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

/**
 * Repository 테스트 공통 설정.
 *
 * ⚠️ Spring Boot 4 는 테스트 슬라이스 애노테이션의 패키지를 옮겼다. Boot 3 예제를 그대로 옮기면 컴파일에 실패한다.
 *    @DataJpaTest               : org.springframework.boot.data.jpa.test.autoconfigure
 *    @AutoConfigureTestDatabase : org.springframework.boot.jdbc.test.autoconfigure
 *
 * - replace = NONE: @DataJpaTest 가 DataSource 를 임베디드 DB 로 바꿔치기하는 것을 막는다.
 *   이 프로젝트는 H2 를 쓰지 않는다(집계가 PostgreSQL 동작에 의존한다).
 * - @Sql: ddl-auto: create-drop 은 매 컨텍스트마다 스키마를 새로 만들므로,
 *   로컬에 수동 적용한 부분 유니크 인덱스와 CHECK 제약이 테스트 DB 에는 없다.
 *   여기서 함께 적용해야 제약 관련 테스트가 의미를 갖는다.
 *   스크립트는 멱등하므로 메서드마다 다시 실행되어도 안전하다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Sql(scripts = "classpath:db/schema-extra.sql")
public abstract class RepositoryTestSupport {
}
