package com.closecircle.api.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Base class for expected API errors. Spring renders it as an RFC 9457 Problem Details body
 * with the stable {@code code} field described in docs/api-contract.md, section 6.
 */
public abstract class ApiProblem extends ErrorResponseException {
    protected ApiProblem(HttpStatus status, String code, String title, String detail) {
        super(status, problem(status, code, title, detail), null);
    }

    private static ProblemDetail problem(HttpStatus status, String code, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        return problem;
    }
}
