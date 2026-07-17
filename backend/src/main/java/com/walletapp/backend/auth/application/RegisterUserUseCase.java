package com.walletapp.backend.auth.application;

import com.walletapp.backend.auth.application.dto.RegisterCommand;
import com.walletapp.backend.auth.application.dto.UserView;
import com.walletapp.backend.auth.domain.Email;
import com.walletapp.backend.auth.domain.PasswordHash;
import com.walletapp.backend.auth.domain.PasswordHasher;
import com.walletapp.backend.auth.domain.RawPassword;
import com.walletapp.backend.auth.domain.User;
import com.walletapp.backend.auth.domain.UserRepository;
import com.walletapp.backend.auth.domain.exception.EmailAlreadyInUseException;
import org.springframework.stereotype.Component;

@Component
public class RegisterUserUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public RegisterUserUseCase(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    public UserView execute(RegisterCommand command) {
        Email email = new Email(command.email());
        if (userRepository.findByEmail(email).isPresent()) {
            throw new EmailAlreadyInUseException("Email already in use: " + email.value());
        }

        RawPassword rawPassword = new RawPassword(command.rawPassword());
        PasswordHash passwordHash = passwordHasher.hash(rawPassword);
        User user = User.register(email, passwordHash, command.displayName());
        User saved = userRepository.save(user);

        return new UserView(saved.id().value(), saved.email().value(), saved.displayName());
    }
}
