package com.inventory.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** ISBN-10 or ISBN-13; hyphens and spaces are allowed. Null/blank means "no ISBN". */
@Documented
@Constraint(validatedBy = ValidIsbnValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidIsbn {

    String message() default "ISBN must have 10 or 13 digits (ISBN-10 may end in X)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
