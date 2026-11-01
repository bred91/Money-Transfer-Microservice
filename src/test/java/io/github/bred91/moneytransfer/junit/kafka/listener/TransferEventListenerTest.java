package io.github.bred91.moneytransfer.junit.kafka.listener;

import io.github.bred91.moneytransfer.dto.TransferEventDTO;
import io.github.bred91.moneytransfer.kafka.listener.TransferEventListener;
import io.github.bred91.moneytransfer.service.TransferProcessingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransferEventListenerTest {

    @Mock
    private TransferProcessingService processingService;

    @InjectMocks
    private TransferEventListener listener;

    @Test
    void onMessage_shouldSkipWhenPayloadIsNull() {
        listener.onMessage(null);
        verifyNoInteractions(processingService);
    }

    @Test
    void onMessage_shouldSkipWhenTransactionIdIsBlank() {
        TransferEventDTO dto = new TransferEventDTO();
        dto.setTransactionId("");
        dto.setFromAccountId(1L);
        dto.setToAccountId(2L);
        dto.setAmount(new BigDecimal("10.00"));

        listener.onMessage(dto);

        verifyNoInteractions(processingService);
    }

    @Test
    void onMessage_shouldSkipWhenAmountIsNegative() {
        TransferEventDTO dto = new TransferEventDTO();
        dto.setTransactionId("tx-neg");
        dto.setFromAccountId(1L);
        dto.setToAccountId(2L);
        dto.setAmount(new BigDecimal("-5.00"));

        listener.onMessage(dto);

        verifyNoInteractions(processingService);
    }

    @Test
    void onMessage_shouldSkipWhenAccountIdsAreNotPositive() {
        TransferEventDTO dto = new TransferEventDTO();
        dto.setTransactionId("tx-zero-id");
        dto.setFromAccountId(0L);
        dto.setToAccountId(2L);
        dto.setAmount(new BigDecimal("10.00"));

        listener.onMessage(dto);

        verifyNoInteractions(processingService);
    }

    @Test
    void onMessage_shouldSkipWhenSourceAndDestinationAreTheSame() {
        TransferEventDTO dto = new TransferEventDTO();
        dto.setTransactionId("tx-same");
        dto.setFromAccountId(5L);
        dto.setToAccountId(5L);
        dto.setAmount(new BigDecimal("10.00"));

        listener.onMessage(dto);

        verifyNoInteractions(processingService);
    }

    @Test
    void onMessage_shouldCallProcessingServiceForValidTransfer() {
        TransferEventDTO dto = new TransferEventDTO();
        dto.setTransactionId("tx-ok");
        dto.setFromAccountId(1L);
        dto.setToAccountId(2L);
        dto.setAmount(new BigDecimal("50.00"));

        listener.onMessage(dto);

        verify(processingService).processTransfer("tx-ok", 1L, 2L, new BigDecimal("50.00"));
    }

    @Test
    void onMessage_shouldRethrowRuntimeExceptionWhenProcessingFails() {
        TransferEventDTO dto = new TransferEventDTO();
        dto.setTransactionId("tx-ex");
        dto.setFromAccountId(1L);
        dto.setToAccountId(2L);
        dto.setAmount(new BigDecimal("20.00"));

        doThrow(new IllegalStateException("boom"))
                .when(processingService).processTransfer("tx-ex", 1L, 2L, new BigDecimal("20.00"));

        assertThatThrownBy(() -> listener.onMessage(dto))
                .isInstanceOf(RuntimeException.class)
                .satisfies(ex -> {
                    Throwable cause = ex.getCause();
                    assertThat(cause).isInstanceOf(IllegalStateException.class);
                });
    }
}
