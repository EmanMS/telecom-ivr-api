package com.vodafone.ivr.dto.response;

/**
 * Caller-facing message text, centralised so that the wording the IVR reads out can never drift
 * between the controller, the service, and the exception handler.
 */
public final class ResponseMessages {

    public static final String RECHARGE_SUCCESSFUL = "Balance has been recharged successfully";
    public static final String TRANSFER_SUCCESSFUL = "Balance has been transferred successfully";
    public static final String SMS_SENT = "The SMS has been sent successfully.";
    public static final String INVALID_DATA = "Invalid data received";
    public static final String RESOURCE_NOT_FOUND = "Requested resource was not found";
    public static final String METHOD_NOT_ALLOWED = "Requested method is not supported";
    public static final String UNEXPECTED_ERROR = "Service is temporarily unavailable";

    private ResponseMessages() {
    }
}
