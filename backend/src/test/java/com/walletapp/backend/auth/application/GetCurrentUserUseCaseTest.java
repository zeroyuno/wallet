package com.walletapp.backend.auth.application;

import com.walletapp.backend.auth.application.dto.UserView;
import com.walletapp.backend.auth.domain.Email;
import com.walletapp.backend.auth.domain.PasswordHash;
import com.walletapp.backend.auth.domain.User;
import com.walletapp.backend.auth.domain.UserId;
import com.walletapp.backend.auth.domain.UserRepository;
import com.walletapp.backend.auth.domain.exception.InvalidCredentialsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCurrentUserUseCaseTest {

    @Mock
    UserRepository userRepository;

    @Test
    void returnsUserViewWhenFound() {
        User user = User.register(new Email("ana@example.com"), new PasswordHash("hashed"), "Ana");
        when(userRepository.findById(any())).thenReturn(Optional.of(user));

        GetCurrentUserUseCase useCase = new GetCurrentUserUseCase(userRepository);
        UserView result = useCase.execute(user.id());

        assertThat(result.email()).isEqualTo("ana@example.com");
        assertThat(result.displayName()).isEqualTo("Ana");
    }

    @Test
    void throwsWhenUserNotFound() {
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        GetCurrentUserUseCase useCase = new GetCurrentUserUseCase(userRepository);

        assertThatThrownBy(() -> useCase.execute(UserId.newId()))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
