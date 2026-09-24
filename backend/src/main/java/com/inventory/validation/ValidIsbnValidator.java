package com.inventory.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidIsbnValidator implements ConstraintValidator<ValidIsbn, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        String normalized = Isbn.normalize(value);
        return normalized == null || Isbn.isWellFormed(normalized);
    }
}
