package io.github.bred91.moneytransfer.junit.service;

import static io.github.bred91.moneytransfer.TestIds.id;

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
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
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

        when(accountRepository.saveAndFlush(any(AccountEntity.class))).thenAnswer(invocation -> {
            AccountEntity e = invocation.getArgument(0);
            e.setId(id(1));
            return e;
        });

        AccountResponseDTO result = accountService.createAccount(req);

        assertThat(result.getId()).isEqualTo(id(1));
        assertThat(result.getOwnerName()).isEqualTo("John");
        assertThat(result.getOwnerSurname()).isEqualTo("Doe");
        assertThat(result.getBalance()).isEqualByComparingTo("100.00");

        ArgumentCaptor<AccountEntity> captor = ArgumentCaptor.forClass(AccountEntity.class);
        verify(accountRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getOwnerName()).isEqualTo("John");
    }

    @Test
    void createAccount_shouldLogTheAccountIdButNotTheOwnerName(CapturedOutput output) {
        CreateAccountRequestDTO req = new CreateAccountRequestDTO();
        req.setOwnerName("Mario");
        req.setOwnerSurname("Rossi");
        req.setInitialBalance(new BigDecimal("100.00"));
        when(accountRepository.saveAndFlush(any(AccountEntity.class))).thenAnswer(invocation -> {
            AccountEntity e = invocation.getArgument(0);
            e.setId(id(7));
            return e;
        });

        accountService.createAccount(req);

        assertThat(output).contains("Created account " + id(7))
                .doesNotContain("Mario")
                .doesNotContain("Rossi");
    }

    @Test
    void getAccount_shouldNotLogAnErrorWhenMissing(CapturedOutput output) {
        when(accountRepository.findById(id(404))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccount(id(404))).isInstanceOf(ResponseStatusException.class);

        assertThat(output).doesNotContain("ERROR");
    }

    @Test
    void getAccount_shouldReturnDtoWhenFound() {
        AccountEntity entity = AccountEntity.builder()
                .id(id(2))
                .ownerName("Alice")
                .ownerSurname("Smith")
                .balance(new BigDecimal("250.00"))
                .createdAt(Instant.parse("2026-02-01T09:00:00Z"))
                .lastModifiedAt(Instant.parse("2026-02-01T09:00:00Z"))
                .build();

        when(accountRepository.findById(id(2))).thenReturn(Optional.of(entity));

        AccountResponseDTO result = accountService.getAccount(id(2));

        assertThat(result.getId()).isEqualTo(id(2));
        assertThat(result.getOwnerName()).isEqualTo("Alice");
        verify(accountRepository).findById(id(2));
    }

    @Test
    void getAccount_shouldThrowNotFoundWhenMissing() {
        when(accountRepository.findById(id(999))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccount(id(999)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(rse.getReason()).isEqualTo("Account not found");
                });

        verify(accountRepository).findById(id(999));
    }

    @Test
    void listAccounts_shouldReturnMappedDtos() {
        List<AccountEntity> entities = List.of(
                AccountEntity.builder().id(id(1)).ownerName("A").ownerSurname("One").balance(new BigDecimal("10.00")).createdAt(Instant.now()).lastModifiedAt(Instant.now()).build(),
                AccountEntity.builder().id(id(2)).ownerName("B").ownerSurname("Two").balance(new BigDecimal("20.00")).createdAt(Instant.now()).lastModifiedAt(Instant.now()).build()
        );

        when(accountRepository.findAll()).thenReturn(entities);

        List<AccountResponseDTO> result = accountService.listAccounts();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(id(1));
        assertThat(result.get(1).getId()).isEqualTo(id(2));
        verify(accountRepository).findAll();
    }
}