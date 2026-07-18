package com.walletapp.backend.auth.infrastructure.web;

import com.walletapp.backend.auth.application.GetCurrentUserUseCase;
import com.walletapp.backend.auth.application.LoginUseCase;
import com.walletapp.backend.auth.application.LogoutUseCase;
import com.walletapp.backend.auth.application.RegisterUserUseCase;
import com.walletapp.backend.auth.application.dto.AuthResult;
import com.walletapp.backend.auth.application.dto.LoginCommand;
import com.walletapp.backend.auth.application.dto.RegisterCommand;
import com.walletapp.backend.auth.application.dto.UserView;
import com.walletapp.backend.auth.domain.UserId;
import com.walletapp.backend.auth.infrastructure.web.dto.AuthResponse;
import com.walletapp.backend.auth.infrastructure.web.dto.LoginRequest;
import com.walletapp.backend.auth.infrastructure.web.dto.RegisterRequest;
import com.walletapp.backend.auth.infrastructure.web.dto.UserResponse;
import com.walletapp.backend.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final RegisterUserUseCase registerUserUseCase;
    private final LoginUseCase loginUseCase;
    private final GetCurrentUserUseCase getCurrentUserUseCase;
    private final LogoutUseCase logoutUseCase;

    public AuthController(RegisterUserUseCase registerUserUseCase, LoginUseCase loginUseCase,
                           GetCurrentUserUseCase getCurrentUserUseCase, LogoutUseCase logoutUseCase) {
        this.registerUserUseCase = registerUserUseCase;
        this.loginUseCase = loginUseCase;
        this.getCurrentUserUseCase = getCurrentUserUseCase;
        this.logoutUseCase = logoutUseCase;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        log.info("POST /api/auth/register email={}", request.email());
        UserView view = registerUserUseCase.execute(
                new RegisterCommand(request.email(), request.password(), request.displayName()));
        log.info("register OK id={}", view.id());
        return new UserResponse(view.id(), view.email(), view.displayName());
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        log.info("POST /api/auth/login email={}", request.email());
        AuthResult result = loginUseCase.execute(new LoginCommand(request.email(), request.password()));
        log.info("login OK email={}", request.email());
        return new AuthResponse(result.accessToken(), result.tokenType(), result.expiresAt());
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        log.info("GET /api/auth/me userId={}", principal.id());
        UserView view = getCurrentUserUseCase.execute(UserId.of(principal.id()));
        return new UserResponse(view.id(), view.email(), view.displayName());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal AuthenticatedUser principal) {
        log.info("POST /api/auth/logout userId={}", principal.id());
        logoutUseCase.execute(principal.jti(), principal.expiresAt());
    }
}
