package dev.springboot4docs.ch_08_bean_validation;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ValidationErrorAdvice {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleInvalidRequest(MethodArgumentNotValidException ex) {
        List<ValidationFieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ValidationFieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(new ValidationErrorResponse(errors));
    }

    public record ValidationErrorResponse(List<ValidationFieldError> errors) {
    }

    public record ValidationFieldError(String field, String message) {
    }

}
