package io.github.bred91.moneytransfer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.github.bred91.moneytransfer.dto.AccountResponseDTO;
import io.github.bred91.moneytransfer.dto.CreateAccountRequestDTO;
import io.github.bred91.moneytransfer.entity.AccountEntity;
import io.github.bred91.moneytransfer.enumeration.AccountStatusEnum;
import io.github.bred91.moneytransfer.repository.AccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Account operations.
 * <p>
 * Failures are not logged here: they propagate to {@link io.github.bred91.moneytransfer.advice.ApiExceptionHandler},
 * which logs unexpected errors once. Owner names are personal data and are never logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    @Transactional
    public AccountResponseDTO createAccount(CreateAccountRequestDTO accountRequest) {
        AccountEntity account = AccountEntity.builder()
                .ownerName(accountRequest.getOwnerName())
                .ownerSurname(accountRequest.getOwnerSurname())
                .status(AccountStatusEnum.ACTIVE)
                .balance(accountRequest.getInitialBalance())
                .build();
        // Flush immediately: the INSERT populates the creation timestamps returned in the response
        account = accountRepository.saveAndFlush(account);

        log.info("Created account {}", account.getId());
        return toResponseDTO(account);
    }

    @Transactional(readOnly = true)
    public AccountResponseDTO getAccount(UUID id) {
        log.debug("Fetching account {}", id);

        AccountEntity account = accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        return toResponseDTO(account);
    }

    @Transactional(readOnly = true)
    public List<AccountResponseDTO> listAccounts() {
        List<AccountResponseDTO> accounts = accountRepository.findAll()
                .stream()
                .map(this::toResponseDTO)
                .toList();

        log.debug("Listed {} accounts", accounts.size());
        return accounts;
    }

    private AccountResponseDTO toResponseDTO(AccountEntity account) {
        return AccountResponseDTO.builder()
                .id(account.getId())
                .ownerName(account.getOwnerName())
                .ownerSurname(account.getOwnerSurname())
                .status(account.getStatus())
                .balance(account.getBalance())
                .createdAt(account.getCreatedAt())
                .lastModifiedAt(account.getLastModifiedAt())
                .build();
    }
}
