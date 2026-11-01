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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    @Transactional
    public AccountResponseDTO createAccount(CreateAccountRequestDTO accountRequest) {
        log.info("Creating new account for owner: {} {}",
                accountRequest.getOwnerName(), accountRequest.getOwnerSurname());

        try {
            AccountEntity account = AccountEntity.builder()
                    .ownerName(accountRequest.getOwnerName())
                    .ownerSurname(accountRequest.getOwnerSurname())
                    .status(AccountStatusEnum.ACTIVE)
                    .balance(accountRequest.getInitialBalance())
                    .build();
            account = accountRepository.save(account);

            log.info("Account created successfully with ID: {} for owner: {} {}",
                    account.getId(), account.getOwnerName(), account.getOwnerSurname());

            return toResponseDTO(account);
        } catch (Exception e) {
            log.error("Failed to create account for owner: {} {}",
                    accountRequest.getOwnerName(), accountRequest.getOwnerSurname(), e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public AccountResponseDTO getAccount(Long id) {
        log.debug("Fetching account with ID: {}", id);

        try {
            AccountEntity account = accountRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

            log.debug("Account retrieved successfully with ID: {}", id);
            return toResponseDTO(account);
        } catch (Exception e) {
            log.error("Error retrieving account with ID: {}", id, e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<AccountResponseDTO> listAccounts() {
        log.debug("Listing all accounts");

        try {
            List<AccountResponseDTO> accounts = accountRepository.findAll()
                    .stream()
                    .map(this::toResponseDTO)
                    .collect(Collectors.toList());

            log.debug("Retrieved {} accounts", accounts.size());
            return accounts;
        } catch (Exception e) {
            log.error("Error retrieving accounts list", e);
            throw e;
        }
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
