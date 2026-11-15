package com.closecircle.api.error;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns request errors into Problem Details responses for every controller.
 * The parent class already handles {@link ApiProblem} and Spring's own MVC exceptions;
 * we only customise the two cases where the contract asks for more detail.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    /** One entry per invalid field. Rejected values are deliberately left out so passwords are never echoed. */
    record FieldProblem(String field, String code, String message) { }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "One or more fields are invalid.");
        problem.setTitle("Request validation failed");
        problem.setProperty("code", "VALIDATION_ERROR");
        List<FieldProblem> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldProblem(error.getField(), fieldCode(error), error.getDefaultMessage()))
                .toList();
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "The request body is missing or is not valid JSON.");
        problem.setTitle("Malformed request");
        problem.setProperty("code", "MALFORMED_REQUEST");
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /** Maps Bean Validation annotation names to the contract's field error codes. */
    private static String fieldCode(FieldError error) {
        String constraint = error.getCode() == null ? "" : error.getCode();
        return switch (constraint) {
            case "NotNull", "NotBlank", "NotEmpty" -> "REQUIRED";
            case "Email" -> "INVALID_FORMAT";
            case "Size", "MaxUtf8Bytes" -> "INVALID_LENGTH";
            default -> "INVALID";
        };
    }
}
