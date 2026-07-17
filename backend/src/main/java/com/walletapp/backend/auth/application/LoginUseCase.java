package com.walletapp.backend.auth.application;

import com.walletapp.backend.auth.application.dto.AuthResult;
import com.walletapp.backend.auth.application.dto.LoginCommand;
import com.walletapp.backend.auth.domain.Email;
import com.walletapp.backend.auth.domain.PasswordHasher;
import com.walletapp.backend.auth.domain.User;
import com.walletapp.backend.auth.domain.UserRepository;
import com.walletapp.backend.auth.domain.exception.AccountLockedException;
import com.walletapp.backend.auth.domain.exception.InvalidCredentialsException;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class LoginUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;

    public LoginUseCase(UserRepository userRepository, PasswordHasher passwordHasher, TokenIssuer tokenIssuer) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
    }

    public AuthResult execute(LoginCommand command) {
        Email email = new Email(command.email());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        Instant now = Instant.now();
        if (user.isLocked(now)) {
            throw new AccountLockedException("Account is temporarily locked due to too many failed login attempts");
        }

        if (!user.verifyPassword(command.rawPassword(), passwordHasher)) {
            user.registerFailedLogin(now);
            userRepository.save(user);
            throw new InvalidCredentialsException("Invalid email or password");
        }

        user.registerSuccessfulLogin();
        userRepository.save(user);

        TokenIssuer.IssuedToken issued = tokenIssuer.issue(user.id(), user.email());
        return new AuthResult(issued.accessToken(), "Bearer", issued.expiresAt());
    }
}
