package com.vodafone.ivr.dto.request;

import com.vodafone.ivr.validation.PhoneNumber;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Body of {@code POST /balance/transfer}.
 *
 * <p>Both numbers reuse the shared {@link PhoneNumber} constraint, so a transfer can never accept
 * a number shape that the VIP lookup or the recharge would reject. Only the message is overridden,
 * so a log line names the field that actually failed rather than a generic "phoneNumber".
 *
 * <p>The rules that <em>relate</em> the two numbers - same length, same three-digit prefix - are
 * cross-field rules and live in {@code TransferService}, not here. A field-level annotation cannot
 * see a sibling field, and expressing that as a class-level custom constraint would bury a
 * business rule in the transport layer where it is harder to find and harder to test.
 */
public record TransferRequest(

        @PhoneNumber(message = "fromNumber must be exactly 11 digits")
        String fromNumber,

        @PhoneNumber(message = "toNumber must be exactly 11 digits")
        String toNumber,

        @NotNull(message = "amount is required")
        @Positive(message = "amount must be greater than zero")
        BigDecimal amount) {
}
