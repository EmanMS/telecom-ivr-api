package com.vodafone.ivr.service;

/**
 * Redacts cardholder and subscriber data before it reaches a log file.
 *
 * <p>Logs are copied to aggregation tools, shared with support staff and kept far longer than a
 * call lasts, so nothing that could be replayed as a payment may enter them. The security code is
 * never masked here because it is never logged at all - PCI DSS forbids storing it after
 * authorisation in any form, redacted or not.
 */
public final class SensitiveDataMasker {

    private static final int VISIBLE_CARD_DIGITS = 4;
    private static final int VISIBLE_PHONE_DIGITS = 3;

    private SensitiveDataMasker() {
    }

    /** Renders a card number as its last four digits only, e.g. {@code ************4242}. */
    public static String maskCardNumber(String cardNumber) {
        return mask(cardNumber, VISIBLE_CARD_DIGITS);
    }

    /**
     * Renders a phone number as its last three digits only. Enough to correlate the log lines of a
     * single call while remaining useless as an identifier on its own.
     */
    public static String maskPhoneNumber(String phoneNumber) {
        return mask(phoneNumber, VISIBLE_PHONE_DIGITS);
    }

    private static String mask(String value, int visibleTrailingChars) {
        if (value == null || value.isBlank()) {
            return "[absent]";
        }
        if (value.length() <= visibleTrailingChars) {
            return "*".repeat(value.length());
        }
        int hidden = value.length() - visibleTrailingChars;
        return "*".repeat(hidden) + value.substring(hidden);
    }
}
