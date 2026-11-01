package io.github.bred91.moneytransfer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.github.bred91.moneytransfer.dto.AccountResponseDTO;
import io.github.bred91.moneytransfer.dto.CreateAccountRequestDTO;
import io.github.bred91.moneytransfer.service.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@Tag(name = "Accounts", description = "Account management endpoints")
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @Operation(summary = "Create a new account", description = "Creates a new account with the specified owner and initial balance")
    @PostMapping
    public ResponseEntity<AccountResponseDTO> create(@Valid @RequestBody CreateAccountRequestDTO accountRequest) {
        AccountResponseDTO createdAccount = accountService.createAccount(accountRequest);
        return ResponseEntity.created(
                ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(createdAccount.getId())
                .toUri()
        ).body(createdAccount);
    }

    @Operation(summary = "Get an account", description = "Retrieves an account by its ID")
    @GetMapping("/{id}")
    public ResponseEntity<AccountResponseDTO> get(@PathVariable UUID id) {
        return ResponseEntity.ok(accountService.getAccount(id));
    }

    @Operation(summary = "List accounts", description = "Retrieves a list of all accounts")
    @GetMapping
    public ResponseEntity<List<AccountResponseDTO>> list() {
        return ResponseEntity.ok(accountService.listAccounts());
    }
}
