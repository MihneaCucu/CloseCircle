package com.closecircle.api.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Limits a string by its UTF-8 size in bytes rather than its length in characters.
 * Needed for passwords: BCrypt only uses the first 72 bytes, and characters such as
 * "ă" or emoji take 2-4 bytes each, so a character limit alone is not enough.
 */
@Documented
@Constraint(validatedBy = MaxUtf8Bytes.Validator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {
    int value();

    String message() default "must be at most {value} bytes";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<MaxUtf8Bytes, String> {
        private int max;

        @Override
        public void initialize(MaxUtf8Bytes annotation) {
            max = annotation.value();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            // null is handled by @NotBlank, as is conventional for Bean Validation constraints.
            return value == null || value.getBytes(StandardCharsets.UTF_8).length <= max;
        }
    }
}
