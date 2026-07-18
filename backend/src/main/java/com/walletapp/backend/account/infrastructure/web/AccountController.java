package com.walletapp.backend.account.infrastructure.web;

import com.walletapp.backend.account.application.AccountService;
import com.walletapp.backend.account.application.dto.AccountCommand;
import com.walletapp.backend.account.application.dto.AccountView;
import com.walletapp.backend.account.domain.AccountId;
import com.walletapp.backend.account.infrastructure.web.dto.AccountRequest;
import com.walletapp.backend.account.infrastructure.web.dto.AccountResponse;
import com.walletapp.backend.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountResponse> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        log.info("GET /api/accounts userId={}", principal.id());
        return accountService.list(principal.id()).stream().map(AccountController::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@AuthenticationPrincipal AuthenticatedUser principal,
                                   @Valid @RequestBody AccountRequest request) {
        log.info("POST /api/accounts userId={} name={}", principal.id(), request.name());
        AccountView view = accountService.create(principal.id(), toCommand(request));
        return toResponse(view);
    }

    @PutMapping("/{id}")
    public AccountResponse update(@AuthenticationPrincipal AuthenticatedUser principal,
                                   @PathVariable UUID id, @Valid @RequestBody AccountRequest request) {
        log.info("PUT /api/accounts/{} userId={}", id, principal.id());
        AccountView view = accountService.update(principal.id(), AccountId.of(id), toCommand(request));
        return toResponse(view);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        log.info("DELETE /api/accounts/{} userId={}", id, principal.id());
        accountService.delete(principal.id(), AccountId.of(id));
    }

    private static AccountCommand toCommand(AccountRequest request) {
        return new AccountCommand(request.name(), request.type(), request.currency(), request.initialBalance());
    }

    private static AccountResponse toResponse(AccountView view) {
        return new AccountResponse(view.id(), view.name(), view.type(), view.currency(), view.initialBalance());
    }
}
