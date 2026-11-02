package io.github.bred91.moneytransfer.controller;

import io.github.bred91.moneytransfer.dto.AccountResponseDTO;
import io.github.bred91.moneytransfer.dto.CreateAccountRequestDTO;
import io.github.bred91.moneytransfer.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@Tag(name = "Accounts", description = "Account management")
@RestController
@RequestMapping(path = "/{version}/accounts", version = "v1", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@ApiResponse(responseCode = "400", description = "Malformed request or unsupported API version",
        content = @Content(mediaType = AccountController.PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
public class AccountController {

    static final String PROBLEM_JSON = "application/problem+json";

    private final AccountService accountService;

    @Operation(summary = "Create an account",
            description = "Creates a new `ACTIVE` account for the given owner with the given initial balance.")
    @ApiResponse(responseCode = "201", description = "Account created",
            headers = @Header(name = "Location", description = "URI of the created account"))
    @ApiResponse(responseCode = "400", description = "Validation failed; `errors` maps each invalid field to its message",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
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

    @Operation(summary = "Get an account", description = "Returns the account with the given ID.")
    @ApiResponse(responseCode = "200", description = "Account found")
    @ApiResponse(responseCode = "404", description = "Account not found",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{id}")
    public ResponseEntity<AccountResponseDTO> get(
            @Parameter(description = "Account ID (UUID)", example = "019a3f6e-1c2b-7d4e-9a10-3b5c7d9e1f20")
            @PathVariable UUID id) {
        return ResponseEntity.ok(accountService.getAccount(id));
    }

    @Operation(summary = "List accounts", description = "Returns all accounts.")
    @ApiResponse(responseCode = "200", description = "Accounts returned (possibly empty)")
    @GetMapping
    public ResponseEntity<List<AccountResponseDTO>> list() {
        return ResponseEntity.ok(accountService.listAccounts());
    }
}
