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
import org.springframework.dao.TransientDataAccessResourceException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Payload validation is applied by the listener container before {@code onMessage} is invoked:
 * see {@code TransferEventDTOValidationTest} and the integration tests.
 */
@ExtendWith(MockitoExtension.class)
public class TransferEventListenerTest {

    @Mock
    private TransferProcessingService processingService;

    @InjectMocks
    private TransferEventListener listener;

    @Test
    void onMessage_shouldCallProcessingServiceForValidTransfer() {
        listener.onMessage(transfer("tx-ok", new BigDecimal("50.00")));

        verify(processingService).processTransfer("tx-ok", id(1), id(2), new BigDecimal("50.00"));
    }

    @Test
    void onMessage_shouldPropagateProcessingFailureUnchangedSoThatItIsRetried() {
        TransientDataAccessResourceException failure = new TransientDataAccessResourceException("Database unavailable");
        doThrow(failure)
                .when(processingService).processTransfer("tx-ex", id(1), id(2), new BigDecimal("20.00"));

        assertThatThrownBy(() -> listener.onMessage(transfer("tx-ex", new BigDecimal("20.00"))))
                .isSameAs(failure);
    }

    private static TransferEventDTO transfer(String transactionId, BigDecimal amount) {
        TransferEventDTO dto = new TransferEventDTO();
        dto.setTransactionId(transactionId);
        dto.setFromAccountId(id(1));
        dto.setToAccountId(id(2));
        dto.setAmount(amount);
        return dto;
    }
}
