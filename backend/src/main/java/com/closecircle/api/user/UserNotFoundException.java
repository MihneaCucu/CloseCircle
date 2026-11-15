package com.closecircle.api.user;

import com.closecircle.api.error.ApiProblem;
import org.springframework.http.HttpStatus;

/** The token is valid but its user no longer exists (for example, the account was deleted). */
public class UserNotFoundException extends ApiProblem {
    public UserNotFoundException() {
        super(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found", "The account no longer exists.");
    }
}
