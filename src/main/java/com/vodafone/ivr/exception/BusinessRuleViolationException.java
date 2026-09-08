package com.vodafone.ivr.exception;

/**
 * Thrown by the service layer when a request is structurally well-formed but breaks a business
 * rule (expired card, amount above the per-transaction ceiling).
 *
 * <p>The message is for logs and tests only - {@code GlobalExceptionHandler} deliberately does not
 * forward it to the caller. Telling an anonymous caller <em>which</em> card detail was wrong is a
 * card-testing oracle, so the API answers every rejected recharge with the same generic text.
 */
public class BusinessRuleViolationException extends RuntimeException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
