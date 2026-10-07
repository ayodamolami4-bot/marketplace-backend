package com.marketplace.backend.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.security.access.AccessDeniedException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedBody(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse(new ApiErrorResponse.ErrorDetail("VALIDATION_ERROR", "Request contains malformed JSON or an invalid field type")));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleOversizedUpload(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(413).body(new ApiErrorResponse(new ApiErrorResponse.ErrorDetail("FILE_TOO_LARGE", "Image exceeds the upload size limit")));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(AccessDeniedException exception) {
        return ResponseEntity.status(403).body(new ApiErrorResponse(new ApiErrorResponse.ErrorDetail("FORBIDDEN", "You do not have permission for this operation")));
    }

    private static final Logger LOGGER =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationException(
            AuthenticationException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new ApiErrorResponse(
                        new ApiErrorResponse.ErrorDetail(
                                "UNAUTHORIZED",
                                "Invalid email or password"
                        )
                ));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(
            ResponseStatusException exception
    ) {
        int status = exception.getStatusCode().value();

        String message = exception.getReason();

        if (message == null || message.isBlank()) {
            message = "Request failed";
        }

        return ResponseEntity
                .status(status)
                .body(new ApiErrorResponse(
                        new ApiErrorResponse.ErrorDetail(
                                codeFor(status),
                                message
                        )
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        String message = "Request validation failed";

        FieldError fieldError =
                exception.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .findFirst()
                        .orElse(null);

        if (fieldError != null
                && fieldError.getDefaultMessage() != null
                && !fieldError.getDefaultMessage().isBlank()) {
            message = fieldError.getField() + ": " + fieldError.getDefaultMessage();
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        new ApiErrorResponse.ErrorDetail(
                                "VALIDATION_ERROR",
                                message
                        )
                ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception
    ) {
        String message = exception.getConstraintViolations()
                .stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse("Request validation failed");

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        new ApiErrorResponse.ErrorDetail(
                                "VALIDATION_ERROR",
                                message
                        )
                ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException exception
    ) {
        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            message = "Invalid request";
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiErrorResponse(
                        new ApiErrorResponse.ErrorDetail(
                                "VALIDATION_ERROR",
                                message
                        )
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception exception
    ) {
        LOGGER.error(
                "Unexpected request failure",
                exception
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(
                        new ApiErrorResponse.ErrorDetail(
                                "INTERNAL_SERVER_ERROR",
                                "An unexpected error occurred"
                        )
                ));
    }

    private String codeFor(int status) {
        return switch (status) {
            case 400 -> "VALIDATION_ERROR";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 409 -> "CONFLICT";
            default -> status >= 500
                    ? "INTERNAL_SERVER_ERROR"
                    : "REQUEST_ERROR";
        };
    }
}
