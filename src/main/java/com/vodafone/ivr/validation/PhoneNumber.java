package com.vodafone.ivr.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Asserts that a value is a valid subscriber phone number: present, and exactly 11 digits.
 *
 * <p>This is a <strong>composed constraint</strong>. It carries no validator of its own - the
 * {@link NotBlank} and {@link Pattern} annotations above it are the actual rules, and Bean
 * Validation applies them wherever this annotation appears. That is why
 * {@code @Constraint(validatedBy = {})} is empty.
 *
 * <p>It exists because {@code phoneNumber} is an input to more than one endpoint: the VIP lookup
 * takes it as a path variable, the recharge takes it as a body field, and any future operation
 * (balance transfer, bundle purchase) will take it too. Repeating {@code @NotBlank @Pattern} at
 * each site means the day the format changes, the endpoints that were missed start disagreeing
 * with the ones that were updated - and a validation rule that holds on one endpoint but not
 * another is a security gap, not just an inconsistency. With one annotation there is exactly one
 * place to change, and every site changes with it.
 *
 * <p>{@link ReportAsSingleViolation} collapses the composed rules into one violation, so a blank
 * number reports "phoneNumber is required" rather than that plus a pattern mismatch.
 */
@Documented
@NotBlank(message = "phoneNumber is required")
@Pattern(regexp = ValidationPatterns.PHONE_NUMBER,
        message = "phoneNumber must be exactly 11 digits")
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface PhoneNumber {

    String message() default "phoneNumber must be exactly 11 digits";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
