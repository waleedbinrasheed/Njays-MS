package com.inventory.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_BYTES = 72;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // @NotBlank reports missing values
        }
        return value.length() >= MIN_LENGTH
                && value.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES
                && value.chars().anyMatch(Character::isLetter)
                && value.chars().anyMatch(Character::isDigit);
    }
}
