package com.walletapp.backend.transaction.infrastructure.web;

import com.walletapp.backend.shared.security.AuthenticatedUser;
import com.walletapp.backend.transaction.application.TransactionService;
import com.walletapp.backend.transaction.infrastructure.web.dto.BalanceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// Vive en el contexto transaction (no en account.infrastructure.web.AccountController) porque el
// saldo es un dato derivado de las transacciones — ver plan.md/research.md de la feature 003.
@RestController
@RequestMapping("/api/accounts/{accountId}/balance")
public class BalanceController {

    private static final Logger log = LoggerFactory.getLogger(BalanceController.class);

    private final TransactionService transactionService;

    public BalanceController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public BalanceResponse get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID accountId) {
        log.info("GET /api/accounts/{}/balance userId={}", accountId, principal.id());
        return new BalanceResponse(accountId, transactionService.getAccountBalance(principal.id(), accountId));
    }
}
