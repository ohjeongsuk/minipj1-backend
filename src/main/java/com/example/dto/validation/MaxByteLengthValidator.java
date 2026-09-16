package com.example.dto.validation;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MaxByteLengthValidator implements ConstraintValidator<MaxByteLength, String> {

    private int max;

    @Override
    public void initialize(MaxByteLength annotation) {
        this.max = annotation.value();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // null 은 @NotBlank 등 다른 제약이 담당한다
        if (value == null) {
            return true;
        }
        return value.getBytes(StandardCharsets.UTF_8).length <= max;
    }
}
