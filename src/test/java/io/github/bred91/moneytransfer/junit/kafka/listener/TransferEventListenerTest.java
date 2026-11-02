package io.github.bred91.moneytransfer.junit.kafka.listener;

import static io.github.bred91.moneytransfer.TestIds.id;

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
        dto.setFromAccountId(id(1));
        dto.setToAccountId(id(2));
        dto.setAmount(new BigDecimal("10.00"));

        listener.onMessage(dto);

        verifyNoInteractions(processingService);
    }

    @Test
    void onMessage_shouldSkipWhenAmountIsNegative() {
        TransferEventDTO dto = new TransferEventDTO();
        dto.setTransactionId("tx-neg");
        dto.setFromAccountId(id(1));
        dto.setToAccountId(id(2));
        dto.setAmount(new BigDecimal("-5.00"));

        listener.onMessage(dto);

        verifyNoInteractions(processingService);
    }

    @Test
    void onMessage_shouldSkipWhenSourceAndDestinationAreTheSame() {
        TransferEventDTO dto = new TransferEventDTO();
        dto.setTransactionId("tx-same");
        dto.setFromAccountId(id(5));
        dto.setToAccountId(id(5));
        dto.setAmount(new BigDecimal("10.00"));

        listener.onMessage(dto);

        verifyNoInteractions(processingService);
    }

    @Test
    void onMessage_shouldCallProcessingServiceForValidTransfer() {
        TransferEventDTO dto = new TransferEventDTO();
        dto.setTransactionId("tx-ok");
        dto.setFromAccountId(id(1));
        dto.setToAccountId(id(2));
        dto.setAmount(new BigDecimal("50.00"));

        listener.onMessage(dto);

        verify(processingService).processTransfer("tx-ok", id(1), id(2), new BigDecimal("50.00"));
    }

    @Test
    void onMessage_shouldRethrowRuntimeExceptionWhenProcessingFails() {
        TransferEventDTO dto = new TransferEventDTO();
        dto.setTransactionId("tx-ex");
        dto.setFromAccountId(id(1));
        dto.setToAccountId(id(2));
        dto.setAmount(new BigDecimal("20.00"));

        doThrow(new IllegalStateException("boom"))
                .when(processingService).processTransfer("tx-ex", id(1), id(2), new BigDecimal("20.00"));

        assertThatThrownBy(() -> listener.onMessage(dto))
                .isInstanceOf(RuntimeException.class)
                .satisfies(ex -> {
                    Throwable cause = ex.getCause();
                    assertThat(cause).isInstanceOf(IllegalStateException.class);
                });
    }
}
