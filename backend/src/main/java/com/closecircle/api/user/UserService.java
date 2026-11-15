package com.closecircle.api.user;

import java.util.UUID;

import com.closecircle.api.error.ApiProblem;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(UUID userId) {
        return users.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(UserNotFoundException::new);
    }

    /** The token is valid but its user no longer exists (for example, the account was deleted). */
    static class UserNotFoundException extends ApiProblem {
        UserNotFoundException() {
            super(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found", "The account no longer exists.");
        }
    }
}
