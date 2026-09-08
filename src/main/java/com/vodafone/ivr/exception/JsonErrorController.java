package com.vodafone.ivr.exception;

import com.vodafone.ivr.dto.response.ApiResponse;
import com.vodafone.ivr.dto.response.ResponseMessages;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replaces Spring Boot's default {@code /error} handling.
 *
 * <p>{@code GlobalExceptionHandler} only sees failures that reach a controller. Errors raised
 * earlier - by the servlet container or a filter - are forwarded to {@code /error} instead, where
 * Boot's default behaviour may render an HTML page or, for some 4xx cases, a body the IVR cannot
 * parse. Both break Call Studio's Rest_Client, so this controller re-serialises those cases into
 * the same {@link ApiResponse} envelope every other endpoint uses.
 */
@RestController
public class JsonErrorController implements ErrorController {

    @RequestMapping(value = "/error", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse> handleContainerError(HttpServletRequest request) {
        HttpStatus status = resolveStatus(request);
        String message = switch (status) {
            case NOT_FOUND -> ResponseMessages.RESOURCE_NOT_FOUND;
            case METHOD_NOT_ALLOWED -> ResponseMessages.METHOD_NOT_ALLOWED;
            default -> status.is4xxClientError()
                    ? ResponseMessages.INVALID_DATA
                    : ResponseMessages.UNEXPECTED_ERROR;
        };
        return ResponseEntity.status(status).body(ApiResponse.failure(message));
    }

    private HttpStatus resolveStatus(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (code == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        HttpStatus resolved = HttpStatus.resolve(Integer.parseInt(code.toString()));
        return resolved != null ? resolved : HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
