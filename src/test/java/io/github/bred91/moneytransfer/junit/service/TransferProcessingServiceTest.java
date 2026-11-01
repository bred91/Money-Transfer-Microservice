package io.github.bred91.moneytransfer.junit.service;

import io.github.bred91.moneytransfer.entity.AccountEntity;
import io.github.bred91.moneytransfer.entity.TransferLogEntity;
import io.github.bred91.moneytransfer.enumeration.AccountStatusEnum;
import io.github.bred91.moneytransfer.repository.AccountRepository;
import io.github.bred91.moneytransfer.service.TransferLogService;
import io.github.bred91.moneytransfer.service.TransferProcessingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransferProcessingServiceTest {

    @Mock
    private TransferLogService transferLogService;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private TransferProcessingService processingService;

    @Test
    void processTransfer_shouldReturnWhenTransactionAlreadyProcessed() {
        when(transferLogService.isAlreadyPresent("tx-already")).thenReturn(true);

        processingService.processTransfer("tx-already", 1L, 2L, new BigDecimal("10.00"));

        verify(transferLogService).isAlreadyPresent("tx-already");
        verify(transferLogService, never()).saveReceived(any(), any(), any(), any());
        verifyNoInteractions(accountRepository);
    }

    @Test
    void processTransfer_shouldMarkFailedWhenAccountsNotFound() {
        when(transferLogService.isAlreadyPresent("tx-missing")).thenReturn(false);
        TransferLogEntity logEntry = TransferLogEntity.builder().transactionId("tx-missing").build();
        when(transferLogService.saveReceived("tx-missing", 1L, 2L, new BigDecimal("5.00"))).thenReturn(logEntry);
        when(accountRepository.findAllByIdForUpdateOrdered(any())).thenReturn(List.of()); // none found

        processingService.processTransfer("tx-missing", 1L, 2L, new BigDecimal("5.00"));

        verify(transferLogService).markFailed(logEntry, "One or both accounts not found");
        verify(accountRepository).findAllByIdForUpdateOrdered(any());
        verify(accountRepository, never()).saveAll(any());
    }

    @Test
    void processTransfer_shouldMarkFailedWhenSourceAccountNotActive() {
        when(transferLogService.isAlreadyPresent("tx-src-inactive")).thenReturn(false);
        TransferLogEntity logEntry = TransferLogEntity.builder().transactionId("tx-src-inactive").build();
        when(transferLogService.saveReceived(any(), any(), any(), any())).thenReturn(logEntry);

        AccountEntity a1 = AccountEntity.builder().id(1L).status(AccountStatusEnum.FROZEN).balance(new BigDecimal("100.00")).build();
        AccountEntity a2 = AccountEntity.builder().id(2L).status(AccountStatusEnum.ACTIVE).balance(new BigDecimal("50.00")).build();
        when(accountRepository.findAllByIdForUpdateOrdered(any())).thenReturn(List.of(a1, a2));

        processingService.processTransfer("tx-src-inactive", 1L, 2L, new BigDecimal("10.00"));

        verify(transferLogService).markFailed(logEntry, "Source account is not active");
        verify(accountRepository, never()).saveAll(any());
    }

    @Test
    void processTransfer_shouldMarkFailedWhenDestinationAccountNotActive() {
        when(transferLogService.isAlreadyPresent("tx-dest-inactive")).thenReturn(false);
        TransferLogEntity logEntry = TransferLogEntity.builder().transactionId("tx-dest-inactive").build();
        when(transferLogService.saveReceived(any(), any(), any(), any())).thenReturn(logEntry);

        AccountEntity a1 = AccountEntity.builder().id(1L).status(AccountStatusEnum.ACTIVE).balance(new BigDecimal("100.00")).build();
        AccountEntity a2 = AccountEntity.builder().id(2L).status(AccountStatusEnum.CLOSED).balance(new BigDecimal("50.00")).build();
        when(accountRepository.findAllByIdForUpdateOrdered(any())).thenReturn(List.of(a1, a2));

        processingService.processTransfer("tx-dest-inactive", 1L, 2L, new BigDecimal("10.00"));

        verify(transferLogService).markFailed(logEntry, "Destination account is not active");
        verify(accountRepository, never()).saveAll(any());
    }

    @Test
    void processTransfer_shouldMarkFailedWhenInsufficientFunds() {
        when(transferLogService.isAlreadyPresent("tx-insufficient")).thenReturn(false);
        TransferLogEntity logEntry = TransferLogEntity.builder().transactionId("tx-insufficient").build();
        when(transferLogService.saveReceived(any(), any(), any(), any())).thenReturn(logEntry);

        AccountEntity from = AccountEntity.builder().id(1L).status(AccountStatusEnum.ACTIVE).balance(new BigDecimal("5.00")).build();
        AccountEntity to = AccountEntity.builder().id(2L).status(AccountStatusEnum.ACTIVE).balance(new BigDecimal("10.00")).build();
        when(accountRepository.findAllByIdForUpdateOrdered(any())).thenReturn(List.of(from, to));

        processingService.processTransfer("tx-insufficient", 1L, 2L, new BigDecimal("20.00"));

        verify(transferLogService).markFailed(logEntry, "Insufficient funds");
        verify(accountRepository, never()).saveAll(any());
    }

    @Test
    void processTransfer_shouldDebitAndCreditAndMarkSuccess_whenFromIdIsLowerThanToId() {
        when(transferLogService.isAlreadyPresent("tx-success-low")).thenReturn(false);
        TransferLogEntity logEntry = TransferLogEntity.builder().transactionId("tx-success-low").build();
        when(transferLogService.saveReceived(any(), any(), any(), any())).thenReturn(logEntry);
        when(accountRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AccountEntity lower = AccountEntity.builder().id(1L).status(AccountStatusEnum.ACTIVE).balance(new BigDecimal("100.00")).build();
        AccountEntity higher = AccountEntity.builder().id(2L).status(AccountStatusEnum.ACTIVE).balance(new BigDecimal("20.00")).build();
        when(accountRepository.findAllByIdForUpdateOrdered(any())).thenReturn(List.of(lower, higher));

        processingService.processTransfer("tx-success-low", 1L, 2L, new BigDecimal("30.00"));

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(accountRepository).saveAll(captor.capture());
        List<AccountEntity> saved = captor.getValue();
        // from (id=1) should be debited
        AccountEntity savedFrom = saved.stream().filter(a -> a.getId().equals(1L)).findFirst().orElseThrow();
        AccountEntity savedTo = saved.stream().filter(a -> a.getId().equals(2L)).findFirst().orElseThrow();

        assertThat(savedFrom.getBalance()).isEqualByComparingTo(new BigDecimal("70.00"));
        assertThat(savedTo.getBalance()).isEqualByComparingTo(new BigDecimal("50.00"));

        verify(transferLogService).markSuccess(logEntry);
    }

    @Test
    void processTransfer_shouldDebitAndCreditAndMarkSuccess_whenFromIdIsHigherThanToId() {
        when(transferLogService.isAlreadyPresent("tx-success-high")).thenReturn(false);
        TransferLogEntity logEntry = TransferLogEntity.builder().transactionId("tx-success-high").build();
        when(transferLogService.saveReceived(any(), any(), any(), any())).thenReturn(logEntry);
        when(accountRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Repository returns accounts ordered by id asc (lower id first)
        AccountEntity lower = AccountEntity.builder().id(2L).status(AccountStatusEnum.ACTIVE).balance(new BigDecimal("50.00")).build();
        AccountEntity higher = AccountEntity.builder().id(5L).status(AccountStatusEnum.ACTIVE).balance(new BigDecimal("200.00")).build();
        when(accountRepository.findAllByIdForUpdateOrdered(any())).thenReturn(List.of(lower, higher));

        // fromId is higher (5), toId is lower (2) -> from should be the 'higher' account
        processingService.processTransfer("tx-success-high", 5L, 2L, new BigDecimal("60.00"));

        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(accountRepository).saveAll(captor.capture());
        List<AccountEntity> saved = captor.getValue();

        AccountEntity savedFrom = saved.stream().filter(a -> a.getId().equals(5L)).findFirst().orElseThrow();
        AccountEntity savedTo = saved.stream().filter(a -> a.getId().equals(2L)).findFirst().orElseThrow();

        assertThat(savedFrom.getBalance()).isEqualByComparingTo(new BigDecimal("140.00"));
        assertThat(savedTo.getBalance()).isEqualByComparingTo(new BigDecimal("110.00"));

        verify(transferLogService).markSuccess(logEntry);
    }
}
