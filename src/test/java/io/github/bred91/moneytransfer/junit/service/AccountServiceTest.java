package io.github.bred91.moneytransfer.junit.service;

import io.github.bred91.moneytransfer.dto.AccountResponseDTO;
import io.github.bred91.moneytransfer.dto.CreateAccountRequestDTO;
import io.github.bred91.moneytransfer.entity.AccountEntity;
import io.github.bred91.moneytransfer.repository.AccountRepository;
import io.github.bred91.moneytransfer.service.AccountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

    @Test
    void createAccount_shouldSaveAndReturnDto() {
        CreateAccountRequestDTO req = new CreateAccountRequestDTO();
        req.setOwnerName("John");
        req.setOwnerSurname("Doe");
        req.setInitialBalance(new BigDecimal("100.00"));

        when(accountRepository.save(any(AccountEntity.class))).thenAnswer(invocation -> {
            AccountEntity e = invocation.getArgument(0);
            e.setId(1L);
            return e;
        });

        AccountResponseDTO result = accountService.createAccount(req);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getOwnerName()).isEqualTo("John");
        assertThat(result.getOwnerSurname()).isEqualTo("Doe");
        assertThat(result.getBalance()).isEqualByComparingTo("100.00");

        ArgumentCaptor<AccountEntity> captor = ArgumentCaptor.forClass(AccountEntity.class);
        verify(accountRepository).save(captor.capture());
        assertThat(captor.getValue().getOwnerName()).isEqualTo("John");
    }

    @Test
    void getAccount_shouldReturnDtoWhenFound() {
        AccountEntity entity = AccountEntity.builder()
                .id(2L)
                .ownerName("Alice")
                .ownerSurname("Smith")
                .balance(new BigDecimal("250.00"))
                .createdAt(LocalDateTime.of(2026, 2, 1, 9, 0))
                .lastModifiedAt(LocalDateTime.of(2026, 2, 1, 9, 0))
                .build();

        when(accountRepository.findById(2L)).thenReturn(Optional.of(entity));

        AccountResponseDTO result = accountService.getAccount(2L);

        assertThat(result.getId()).isEqualTo(2L);
        assertThat(result.getOwnerName()).isEqualTo("Alice");
        verify(accountRepository).findById(2L);
    }

    @Test
    void getAccount_shouldThrowNotFoundWhenMissing() {
        when(accountRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccount(999L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(rse.getReason()).isEqualTo("Account not found");
                });

        verify(accountRepository).findById(999L);
    }

    @Test
    void listAccounts_shouldReturnMappedDtos() {
        List<AccountEntity> entities = List.of(
                AccountEntity.builder().id(1L).ownerName("A").ownerSurname("One").balance(new BigDecimal("10.00")).createdAt(LocalDateTime.now()).lastModifiedAt(LocalDateTime.now()).build(),
                AccountEntity.builder().id(2L).ownerName("B").ownerSurname("Two").balance(new BigDecimal("20.00")).createdAt(LocalDateTime.now()).lastModifiedAt(LocalDateTime.now()).build()
        );

        when(accountRepository.findAll()).thenReturn(entities);

        List<AccountResponseDTO> result = accountService.listAccounts();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(1).getId()).isEqualTo(2L);
        verify(accountRepository).findAll();
    }
}