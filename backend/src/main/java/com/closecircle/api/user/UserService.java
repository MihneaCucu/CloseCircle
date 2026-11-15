package com.closecircle.api.user;

import java.util.UUID;

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
}
