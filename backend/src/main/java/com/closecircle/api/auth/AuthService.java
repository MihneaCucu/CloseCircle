package com.closecircle.api.auth;

import java.time.Instant;
import java.util.Optional;

import com.closecircle.api.error.ApiProblem;
import com.closecircle.api.user.User;
import com.closecircle.api.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;

    /**
     * A real BCrypt hash of a throwaway value. When the email is unknown we still compare
     * against it, so a missing account takes as long to reject as a wrong password.
     */
    private final String unknownUserHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.unknownUserHash = passwordEncoder.encode("closecircle-unknown-user");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw new EmailAlreadyRegisteredException();
        }
        var user = new User(request.email(), passwordEncoder.encode(request.password()),
                request.displayName(), Instant.now());
        try {
            // Flush now so a duplicate is detected here, not at commit time.
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two registrations with the same email at the same moment: the unique index stops the second one.
            throw new EmailAlreadyRegisteredException();
        }
        return AuthResponse.bearer(tokens.issue(user.getId()), user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Optional<User> user = users.findByEmailIgnoreCase(request.email());
        String hash = user.map(User::getPasswordHash).orElse(unknownUserHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !passwordMatches) {
            // Same error for "no such account" and "wrong password", so the response does not reveal which emails exist.
            throw new InvalidCredentialsException();
        }
        return AuthResponse.bearer(tokens.issue(user.get().getId()), user.get());
    }

    static class EmailAlreadyRegisteredException extends ApiProblem {
        EmailAlreadyRegisteredException() {
            super(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "Email already registered",
                    "An account with this email already exists.");
        }
    }

    static class InvalidCredentialsException extends ApiProblem {
        InvalidCredentialsException() {
            super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid credentials",
                    "The email or password is incorrect.");
        }
    }
}
