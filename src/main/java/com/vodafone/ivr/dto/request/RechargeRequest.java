package com.vodafone.ivr.dto.request;

import com.vodafone.ivr.validation.PhoneNumber;
import com.vodafone.ivr.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Body of {@code POST /balance/recharge}.
 *
 * <p>Only <em>structural</em> rules live here as Bean Validation annotations: presence and exact
 * format. Every format is a constant in {@link ValidationPatterns} rather than a literal, so the
 * same rule cannot be spelled two different ways in two different files. {@code phoneNumber} goes
 * one step further and uses the shared {@link PhoneNumber} constraint, because it is an input to
 * more than one endpoint.
 *
 * <p>Rules that depend on state or context - "the expiry date must be in the future" and "the
 * amount must not exceed the per-transaction ceiling" - are business rules and are enforced in
 * {@code RechargeService}, where they can be changed, tested, and (later) sourced from
 * configuration or a database without touching the transport layer.
 *
 * <p>{@code amount} is a {@link BigDecimal} rather than a {@code double} because it is money:
 * binary floating point cannot represent values such as 0.10 exactly, and comparisons against a
 * ceiling must be exact.
 */
public record RechargeRequest(

        @PhoneNumber
        String phoneNumber,

        @NotBlank(message = "cardNumber is required")
        @Pattern(regexp = ValidationPatterns.CARD_NUMBER,
                message = "cardNumber must be exactly 16 digits")
        String cardNumber,

        @NotBlank(message = "expiryDate is required")
        @Pattern(regexp = ValidationPatterns.EXPIRY_DATE,
                message = "expiryDate must be in MM/yy format")
        String expiryDate,

        @NotBlank(message = "securityCode is required")
        @Pattern(regexp = ValidationPatterns.SECURITY_CODE,
                message = "securityCode must be exactly 3 digits")
        String securityCode,

        @NotNull(message = "amount is required")
        @Positive(message = "amount must be greater than zero")
        BigDecimal amount) {
}
