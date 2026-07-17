package com.walletapp.backend.auth.application;

import com.walletapp.backend.auth.application.dto.AuthResult;
import com.walletapp.backend.auth.application.dto.LoginCommand;
import com.walletapp.backend.auth.domain.Email;
import com.walletapp.backend.auth.domain.PasswordHash;
import com.walletapp.backend.auth.domain.PasswordHasher;
import com.walletapp.backend.auth.domain.User;
import com.walletapp.backend.auth.domain.UserId;
import com.walletapp.backend.auth.domain.UserRepository;
import com.walletapp.backend.auth.domain.exception.AccountLockedException;
import com.walletapp.backend.auth.domain.exception.InvalidCredentialsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginUseCaseTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordHasher passwordHasher;

    @Mock
    TokenIssuer tokenIssuer;

    private User existingUser() {
        return User.register(new Email("ana@example.com"), new PasswordHash("hashed"), "Ana");
    }

    @Test
    void returnsTokenOnSuccessfulLogin() {
        User user = existingUser();
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(user));
        when(passwordHasher.matches(any(), any())).thenReturn(true);
        when(tokenIssuer.issue(any(), any())).thenReturn(
                new TokenIssuer.IssuedToken("token-value", "jti-1", Instant.now().plusSeconds(3600)));

        LoginUseCase useCase = new LoginUseCase(userRepository, passwordHasher, tokenIssuer);
        AuthResult result = useCase.execute(new LoginCommand("ana@example.com", "password123"));

        assertThat(result.accessToken()).isEqualTo("token-value");
        assertThat(result.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void rejectsWrongPasswordAndRegistersFailedAttempt() {
        User user = existingUser();
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(user));
        when(passwordHasher.matches(any(), any())).thenReturn(false);

        LoginUseCase useCase = new LoginUseCase(userRepository, passwordHasher, tokenIssuer);

        assertThatThrownBy(() -> useCase.execute(new LoginCommand("ana@example.com", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().failedLoginAttempts()).isEqualTo(1);
    }

    @Test
    void rejectsLoginForNonexistentEmail() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

        LoginUseCase useCase = new LoginUseCase(userRepository, passwordHasher, tokenIssuer);

        assertThatThrownBy(() -> useCase.execute(new LoginCommand("ghost@example.com", "password123")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void rejectsLoginWhenAccountIsLocked() {
        User lockedUser = User.reconstitute(
                UserId.of(UUID.randomUUID()),
                new Email("ana@example.com"),
                new PasswordHash("hashed"),
                "Ana",
                Instant.now().minus(1, ChronoUnit.DAYS),
                5,
                Instant.now().plusSeconds(600));
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(lockedUser));

        LoginUseCase useCase = new LoginUseCase(userRepository, passwordHasher, tokenIssuer);

        assertThatThrownBy(() -> useCase.execute(new LoginCommand("ana@example.com", "password123")))
                .isInstanceOf(AccountLockedException.class);
    }
}
