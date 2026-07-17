package com.walletapp.backend.auth.application;

import com.walletapp.backend.auth.application.dto.RegisterCommand;
import com.walletapp.backend.auth.application.dto.UserView;
import com.walletapp.backend.auth.domain.Email;
import com.walletapp.backend.auth.domain.PasswordHash;
import com.walletapp.backend.auth.domain.PasswordHasher;
import com.walletapp.backend.auth.domain.User;
import com.walletapp.backend.auth.domain.UserRepository;
import com.walletapp.backend.auth.domain.exception.EmailAlreadyInUseException;
import com.walletapp.backend.auth.domain.exception.InvalidPasswordException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterUserUseCaseTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordHasher passwordHasher;

    @Test
    void registersNewUserWithHashedPassword() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(passwordHasher.hash(any())).thenReturn(new PasswordHash("hashed-value"));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RegisterUserUseCase useCase = new RegisterUserUseCase(userRepository, passwordHasher);
        UserView result = useCase.execute(new RegisterCommand("ana@example.com", "password123", "Ana"));

        assertThat(result.email()).isEqualTo("ana@example.com");
        assertThat(result.displayName()).isEqualTo("Ana");
        verify(userRepository).save(any());
    }

    @Test
    void rejectsRegistrationWhenEmailAlreadyExists() {
        User existing = User.register(new Email("ana@example.com"), new PasswordHash("x"), "Ana");
        when(userRepository.findByEmail(any())).thenReturn(Optional.of(existing));

        RegisterUserUseCase useCase = new RegisterUserUseCase(userRepository, passwordHasher);

        assertThatThrownBy(() -> useCase.execute(new RegisterCommand("ana@example.com", "password123", "Ana")))
                .isInstanceOf(EmailAlreadyInUseException.class);
    }

    @Test
    void rejectsRegistrationWithShortPassword() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

        RegisterUserUseCase useCase = new RegisterUserUseCase(userRepository, passwordHasher);

        assertThatThrownBy(() -> useCase.execute(new RegisterCommand("ana@example.com", "short", "Ana")))
                .isInstanceOf(InvalidPasswordException.class);
    }
}
