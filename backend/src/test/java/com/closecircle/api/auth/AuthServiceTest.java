package com.closecircle.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.closecircle.api.user.User;
import com.closecircle.api.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/** Unit tests: the repository, password encoder and token service are mocks. */
class AuthServiceTest {
    UserRepository users = mock(UserRepository.class);
    PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    TokenService tokens = mock(TokenService.class);
    AuthService service;

    @BeforeEach
    void setUp() {
        given(passwordEncoder.encode("closecircle-unknown-user")).willReturn("unknown-user-hash");
        given(tokens.issue(any())).willReturn(new TokenService.IssuedToken("token", Instant.now().plusSeconds(3600)));
        service = new AuthService(users, passwordEncoder, tokens);
    }

    @Test
    void registerStoresOnlyTheHash() {
        given(passwordEncoder.encode("correct-horse-example")).willReturn("bcrypt-hash");
        given(users.saveAndFlush(any())).willAnswer(call -> call.getArgument(0));

        AuthResponse response = service.register(
                new RegisterRequest("alex@example.invalid", "correct-horse-example", "Alex"));

        verify(users).saveAndFlush(argThat(user -> user.getPasswordHash().equals("bcrypt-hash")));
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().email()).isEqualTo("alex@example.invalid");
    }

    @Test
    void registerRejectsAnExistingEmail() {
        given(users.existsByEmailIgnoreCase("alex@example.invalid")).willReturn(true);

        assertThatThrownBy(() -> service.register(
                new RegisterRequest("alex@example.invalid", "correct-horse-example", "Alex")))
                .isInstanceOf(AuthService.EmailAlreadyRegisteredException.class);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void registerTurnsAConcurrentDuplicateIntoAConflict() {
        given(passwordEncoder.encode(anyString())).willReturn("bcrypt-hash");
        given(users.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("users_email_lower_uq"));

        assertThatThrownBy(() -> service.register(
                new RegisterRequest("alex@example.invalid", "correct-horse-example", "Alex")))
                .isInstanceOf(AuthService.EmailAlreadyRegisteredException.class);
    }

    @Test
    void loginWithCorrectPasswordIssuesToken() {
        User user = existingUser();
        given(users.findByEmailIgnoreCase("alex@example.invalid")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("correct-horse-example", "stored-hash")).willReturn(true);

        AuthResponse response = service.login(new LoginRequest("alex@example.invalid", "correct-horse-example"));

        assertThat(response.accessToken()).isEqualTo("token");
        verify(tokens).issue(user.getId());
    }

    @Test
    void loginWithWrongPasswordFails() {
        given(users.findByEmailIgnoreCase("alex@example.invalid")).willReturn(Optional.of(existingUser()));
        given(passwordEncoder.matches("wrong-password-123", "stored-hash")).willReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("alex@example.invalid", "wrong-password-123")))
                .isInstanceOf(AuthService.InvalidCredentialsException.class);
    }

    @Test
    void loginWithUnknownEmailFailsTheSameWayAndStillChecksAPassword() {
        given(users.findByEmailIgnoreCase("nobody@example.invalid")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("nobody@example.invalid", "whatever-password")))
                .isInstanceOf(AuthService.InvalidCredentialsException.class);
        // The comparison still runs, so timing does not reveal that the account is missing.
        verify(passwordEncoder).matches(eq("whatever-password"), eq("unknown-user-hash"));
    }

    private static User existingUser() {
        var user = new User("alex@example.invalid", "stored-hash", "Alex", Instant.now());
        // The database normally generates the ID.
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }
}
