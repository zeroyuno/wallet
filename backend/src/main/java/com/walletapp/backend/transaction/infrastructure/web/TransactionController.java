package com.walletapp.backend.transaction.infrastructure.web;

import com.walletapp.backend.shared.security.AuthenticatedUser;
import com.walletapp.backend.transaction.application.TransactionService;
import com.walletapp.backend.transaction.application.dto.TransactionCommand;
import com.walletapp.backend.transaction.application.dto.TransactionFilter;
import com.walletapp.backend.transaction.application.dto.TransactionUpdateCommand;
import com.walletapp.backend.transaction.application.dto.TransactionView;
import com.walletapp.backend.transaction.domain.TransactionId;
import com.walletapp.backend.transaction.infrastructure.web.dto.TransactionRequest;
import com.walletapp.backend.transaction.infrastructure.web.dto.TransactionResponse;
import com.walletapp.backend.transaction.infrastructure.web.dto.TransactionUpdateRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public List<TransactionResponse> list(@AuthenticationPrincipal AuthenticatedUser principal,
                                           @RequestParam(required = false) UUID accountId,
                                           @RequestParam(required = false) UUID categoryId,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        log.info("GET /api/transactions userId={} accountId={} categoryId={} dateFrom={} dateTo={}",
                principal.id(), accountId, categoryId, dateFrom, dateTo);
        TransactionFilter filter = new TransactionFilter(accountId, categoryId, dateFrom, dateTo);
        return transactionService.list(principal.id(), filter).stream()
                .map(TransactionController::toResponse)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse create(@AuthenticationPrincipal AuthenticatedUser principal,
                                       @Valid @RequestBody TransactionRequest request) {
        log.info("POST /api/transactions userId={} accountId={}", principal.id(), request.accountId());
        TransactionView view = transactionService.create(principal.id(), toCommand(request));
        return toResponse(view);
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        log.info("GET /api/transactions/{} userId={}", id, principal.id());
        return toResponse(transactionService.get(principal.id(), TransactionId.of(id)));
    }

    @PutMapping("/{id}")
    public TransactionResponse update(@AuthenticationPrincipal AuthenticatedUser principal,
                                       @PathVariable UUID id, @Valid @RequestBody TransactionUpdateRequest request) {
        log.info("PUT /api/transactions/{} userId={}", id, principal.id());
        TransactionView view = transactionService.update(principal.id(), TransactionId.of(id), toCommand(request));
        return toResponse(view);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable UUID id) {
        log.info("DELETE /api/transactions/{} userId={}", id, principal.id());
        transactionService.delete(principal.id(), TransactionId.of(id));
    }

    private static TransactionUpdateCommand toCommand(TransactionUpdateRequest request) {
        return new TransactionUpdateCommand(request.amount(), request.date(), request.description(),
                request.categoryId());
    }

    private static TransactionCommand toCommand(TransactionRequest request) {
        return new TransactionCommand(request.id(), request.type(), request.amount(), request.date(),
                request.description(), request.accountId(), request.categoryId());
    }

    private static TransactionResponse toResponse(TransactionView view) {
        return new TransactionResponse(view.id(), view.type(), view.amount(), view.date(), view.description(),
                view.accountId(), view.categoryId(), view.counterParty(), view.paymentType(), view.recordState(),
                view.walletTransferId(), view.labels());
    }
}
