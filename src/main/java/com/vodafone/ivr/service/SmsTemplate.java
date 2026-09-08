package com.vodafone.ivr.service;

import java.util.Arrays;
import java.util.Optional;

/**
 * The catalogue of SMS templates the IVR may ask this API to send.
 *
 * <p>It lives in the service layer, not in {@code dto}, because it is business vocabulary rather
 * than transport structure. When the catalogue moves to a database in the persistence phase, this
 * enum becomes a repository lookup and no DTO, controller, or IVR script has to change.
 */
public enum SmsTemplate {

    INTERNET_PACKAGES,
    CALL_TONES,
    PROMOTIONS;

    /**
     * Resolves a template code from the wire.
     *
     * <p>Returns an {@link Optional} rather than throwing, so the caller decides what an unknown
     * code means - here, {@code SmsService} turns it into a business-rule violation. Matching is
     * exact and case-sensitive: the IVR sends a fixed string from its own configuration, so a
     * lower-case or padded value means something is misconfigured upstream, and quietly accepting
     * it would hide that.
     */
    public static Optional<SmsTemplate> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(template -> template.name().equals(code))
                .findFirst();
    }
}
