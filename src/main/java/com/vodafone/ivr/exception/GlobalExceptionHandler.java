package com.vodafone.ivr.exception;

import com.vodafone.ivr.dto.response.ApiResponse;
import com.vodafone.ivr.dto.response.ResponseMessages;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Converts every exception that escapes a controller into the standard {@link ApiResponse}
 * envelope.
 *
 * <p>Two rules are applied consistently:
 * <ul>
 *   <li><strong>Nothing internal leaks.</strong> Stack traces, exception class names and framework
 *       messages are logged, never serialised. The caller always sees curated text.</li>
 *   <li><strong>No response is ever empty.</strong> Call Studio's Rest_Client treats a zero-length
 *       body as a transport error, which would surface to the caller as a dropped call rather than
 *       a spoken error message.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Bean Validation failure on an {@code @Valid @RequestBody} argument. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse> handleBodyValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Request body validation failed: {}", details);
        return badRequest();
    }

    /** Bean Validation failure on a path variable or request parameter. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse> handleParameterValidation(ConstraintViolationException ex) {
        String details = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("Request parameter validation failed: {}", details);
        return badRequest();
    }

    /** A business rule rejected an otherwise well-formed request. */
    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ApiResponse> handleBusinessRule(BusinessRuleViolationException ex) {
        log.warn("Business rule rejected the request: {}", ex.getMessage());
        return badRequest();
    }

    /** Body was absent, truncated, or not parseable as JSON. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("Request body could not be parsed: {}", ex.getMostSpecificCause().getMessage());
        return badRequest();
    }

    /** A path variable or query parameter could not be converted to its target type. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Parameter '{}' had an unusable value", ex.getName());
        return badRequest();
    }

    /** Unknown URL. Answered with JSON so the IVR never receives an HTML error page. */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse> handleNotFound(NoHandlerFoundException ex) {
        log.warn("No handler for {} {}", ex.getHttpMethod(), ex.getRequestURL());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure(ResponseMessages.RESOURCE_NOT_FOUND));
    }

    /**
     * Spring 6.x raises this instead of {@code NoHandlerFoundException} when a request matches no
     * mapping and no static resource. Both are handled so the 404 contract holds either way.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse> handleMissingResource(NoResourceFoundException ex) {
        log.warn("No resource for {}", ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure(ResponseMessages.RESOURCE_NOT_FOUND));
    }

    /** Known URL, wrong HTTP verb. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {
        log.warn("Unsupported method {} for this endpoint", ex.getMethod());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.failure(ResponseMessages.METHOD_NOT_ALLOWED));
    }

    /** Last line of defence: anything unanticipated still leaves as valid JSON. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception while serving a request", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(ResponseMessages.UNEXPECTED_ERROR));
    }

    private ResponseEntity<ApiResponse> badRequest() {
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(ResponseMessages.INVALID_DATA));
    }
}
