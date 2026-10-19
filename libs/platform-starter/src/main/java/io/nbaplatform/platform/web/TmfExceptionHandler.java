package io.nbaplatform.platform.web;

import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps exceptions to TMF630 error responses. */
@RestControllerAdvice
public class TmfExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<TmfError> notFound(NotFoundException e) {
        return respond(404, "notFound", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<TmfError> invalid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return respond(400, "invalidBody", message);
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    ResponseEntity<TmfError> badRequest(Exception e) {
        return respond(400, "badRequest", e.getMessage());
    }

    private static ResponseEntity<TmfError> respond(int status, String reason, String message) {
        return ResponseEntity.status(status).body(TmfError.of(status, reason, message));
    }
}
