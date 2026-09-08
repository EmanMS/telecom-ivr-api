package com.vodafone.ivr.validation;

/**
 * The single definition of every input format this API accepts.
 *
 * <p>Each pattern is anchored implicitly: {@code @Pattern} matches the <em>whole</em> value, so
 * {@code \d{11}} accepts a string of exactly eleven digits and nothing longer or shorter.
 *
 * <p>These are compile-time constants specifically so they can be referenced from annotation
 * attributes ({@code @Pattern(regexp = ValidationPatterns.PHONE_NUMBER)}), which only accept
 * constant expressions. Every constraint in the application resolves to a constant here - a format
 * literal written inline anywhere else is a bug waiting to happen the next time one of them
 * changes.
 */
public final class ValidationPatterns {

    /** Subscriber MSISDN: exactly 11 digits, e.g. {@code 01234567890}. */
    public static final String PHONE_NUMBER = "\\d{11}";

    /** Payment card PAN: exactly 16 digits, e.g. {@code 4242424242424242}. */
    public static final String CARD_NUMBER = "\\d{16}";

    /** Card security code: exactly 3 digits, e.g. {@code 567}. */
    public static final String SECURITY_CODE = "\\d{3}";

    /**
     * Card expiry in {@code MM/yy}: a month of 01-12, a slash, then a two-digit year,
     * e.g. {@code 12/34}. The alternation rejects month 00 and 13-99, which a plain
     * {@code \d{2}} would let through.
     *
     * <p>This checks the <em>shape</em> only. Whether the date is still in the future is a
     * business rule, enforced by {@code RechargeService} against an injected clock.
     */
    public static final String EXPIRY_DATE = "(0[1-9]|1[0-2])/\\d{2}";

    private ValidationPatterns() {
    }
}
