package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// @Configuration 클래스가 아니라 여기에 붙인다.
// @DataJpaTest 는 메인 클래스를 설정 소스로 찾아가지만 일반 @Configuration 은 로드하지 않아,
// 다른 곳에 두면 테스트에서 created_at 이 null 이 된다.
@EnableJpaAuditing
@SpringBootApplication
public class ProjectBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProjectBackendApplication.class, args);
	}

}
