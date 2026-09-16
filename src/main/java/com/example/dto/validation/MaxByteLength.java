package com.example.dto.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * UTF-8 인코딩 기준 바이트 길이 상한.
 *
 * @Size(max = N) 은 문자 수를 세므로 한글 비밀번호를 걸러내지 못한다.
 * BCrypt 의 한계는 72바이트인데 한글 1자는 3바이트라 한글 25자 = 75바이트로 이미 한계를 넘는다.
 * 그런데 @Size(max = 64) 는 이 입력을 통과시키고, BCryptPasswordEncoder 가
 * IllegalArgumentException("password cannot be more than 72 bytes") 를 던져 500 이 나간다.
 */
@Documented
@Constraint(validatedBy = MaxByteLengthValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxByteLength {

    int value();

    String message() default "입력값이 너무 깁니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
