package com.closecircle.api.sharing;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class GroupNotFoundException extends ErrorResponseException {
    public GroupNotFoundException() {
        super(HttpStatus.NOT_FOUND, problem(), null);
    }

    private static ProblemDetail problem() {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Group not found.");
        problem.setTitle("Group not found");
        problem.setProperty("code", "GROUP_NOT_FOUND");
        return problem;
    }
}
