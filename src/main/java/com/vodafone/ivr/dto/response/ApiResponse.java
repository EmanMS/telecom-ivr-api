package com.vodafone.ivr.dto.response;

/**
 * The single response envelope used by every non-trivial outcome in this API: recharge successes,
 * validation failures, and unexpected server errors alike.
 *
 * <p>Keeping one shape is a deliberate contract decision. The IVR side parses responses with Call
 * Studio's Rest_Client element, which is far easier to configure when the document it must read is
 * identical on the happy path and every error path - the flow reads {@code success} and, if it
 * needs to speak the reason, {@code message}.
 */
public record ApiResponse(boolean success, String message) {

    public static ApiResponse success(String message) {
        return new ApiResponse(true, message);
    }

    public static ApiResponse failure(String message) {
        return new ApiResponse(false, message);
    }
}
