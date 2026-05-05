package com.example.sportsevents.exception;

import com.example.sportsevents.api.dto.ErrorResponse;
import com.fasterxml.jackson.databind.JsonMappingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Bean validation failed — one or more fields in the request body violated a @NotNull,
    // @NotBlank, etc. constraint. We collect all failing fields into the "details" array so the
    // caller can fix everything in one round trip rather than discovering errors one at a time.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .toList();
        log.debug("Validation failed: {}", details);
        return ResponseEntity.badRequest().body(
                ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "Bad Request", "Validation failed", details)
        );
    }

    // The request body could not be parsed — either the JSON is syntactically broken, or a field
    //Jackson wraps the real error message several layers deep; extractMessage() digs it out so the client receives
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        String detail = extractMessage(ex);
        log.debug("Malformed request body: {}", detail);
        return ResponseEntity.badRequest().body(
                ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "Bad Request", detail, List.of())
        );
    }

    // The caller used the wrong HTTP method (e.g. GET instead of POST). Without this handler the
    // exception would fall through to the catch-all below and return 500; the correct status is 405.
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        String detail = "HTTP method '%s' is not supported for this endpoint.".formatted(ex.getMethod());
        log.debug("Method not allowed: {}", detail);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(
                ErrorResponse.of(HttpStatus.METHOD_NOT_ALLOWED.value(), "Method Not Allowed", detail, List.of())
        );
    }

    // The caller sent a body with an unsupported Content-Type (e.g. text/plain instead of
    // application/json). Without this handler the exception would return 500; the correct status is 415.
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        String detail = "Content-Type '%s' is not supported. Use 'application/json'.".formatted(ex.getContentType());
        log.debug("Unsupported media type: {}", detail);
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(
                ErrorResponse.of(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), "Unsupported Media Type", detail, List.of())
        );
    }

    // Safety net for anything not covered above. We log the full stack trace here because this
    // should never happen in normal operation — if it does, we want to know about it.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error",
                        "An unexpected error occurred", List.of())
        );
    }

    // Jackson wraps the real cause several levels deep. We walk the chain looking for a
    // JsonMappingException. If its direct cause is an IllegalArgumentException (thrown by our
    // @JsonCreator in EventStatus), we surface that message — it's the most specific one.
    // Otherwise we fall back to Jackson's own "original message", and finally to a generic string.
    private String extractMessage(HttpMessageNotReadableException ex) {
        for (Throwable t = ex.getCause(); t != null; t = t.getCause()) {
            if (t instanceof JsonMappingException jme) {
                Throwable inner = jme.getCause();
                if (inner instanceof IllegalArgumentException iae && iae.getMessage() != null) {
                    return iae.getMessage();
                }
                String original = jme.getOriginalMessage();
                if (original != null && !original.isBlank()) {
                    return original;
                }
            }
        }
        return "Malformed JSON body";
    }

    private String formatFieldError(FieldError fieldError) {
        return "%s: %s".formatted(fieldError.getField(), fieldError.getDefaultMessage());
    }
}
