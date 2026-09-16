package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 컨텍스트 로딩 확인.
 * @ActiveProfiles("test") 가 없으면 local 프로파일로 돌아 개발 DB 를 건드리고,
 * application-test.yml 이 검증되지 않은 채 남는다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ProjectBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
