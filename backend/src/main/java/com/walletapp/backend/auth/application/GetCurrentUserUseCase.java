package com.walletapp.backend.auth.application;

import com.walletapp.backend.auth.application.dto.UserView;
import com.walletapp.backend.auth.domain.User;
import com.walletapp.backend.auth.domain.UserId;
import com.walletapp.backend.auth.domain.UserRepository;
import com.walletapp.backend.auth.domain.exception.InvalidCredentialsException;
import org.springframework.stereotype.Component;

@Component
public class GetCurrentUserUseCase {

    private final UserRepository userRepository;

    public GetCurrentUserUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserView execute(UserId userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));
        return new UserView(user.id().value(), user.email().value(), user.displayName());
    }
}
