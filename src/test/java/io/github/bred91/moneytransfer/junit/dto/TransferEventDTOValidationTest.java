package io.github.bred91.moneytransfer.junit.dto;

import static io.github.bred91.moneytransfer.TestIds.id;

import io.github.bred91.moneytransfer.dto.TransferEventDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TransferEventDTOValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "25.50", "100", "99999999999999999.99"})
    void shouldAcceptValidTransfer(String amount) {
        assertThat(validator.validate(transfer("tx-1", id(1), id(2), new BigDecimal(amount)))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void shouldRejectBlankTransactionId(String transactionId) {
        assertThat(invalidFields(transfer(transactionId, id(1), id(2), new BigDecimal("10.00"))))
                .containsExactly("transactionId");
    }

    @Test
    void shouldRejectMissingFields() {
        assertThat(invalidFields(new TransferEventDTO()))
                .containsExactlyInAnyOrder("transactionId", "fromAccountId", "toAccountId", "amount");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-5.00"})
    void shouldRejectNonPositiveAmount(String amount) {
        assertThat(invalidFields(transfer("tx-1", id(1), id(2), new BigDecimal(amount))))
                .containsExactly("amount");
    }

    // An amount with more than 2 decimals would be rounded by the NUMERIC(19, 2) columns, creating or destroying money
    @ParameterizedTest
    @ValueSource(strings = {"0.005", "10.001", "100000000000000000.00"})
    void shouldRejectAmountThatDoesNotFitTheBalanceColumns(String amount) {
        assertThat(invalidFields(transfer("tx-1", id(1), id(2), new BigDecimal(amount))))
                .containsExactly("amount");
    }

    @Test
    void shouldRejectTransferBetweenTheSameAccount() {
        assertThat(invalidFields(transfer("tx-1", id(5), id(5), new BigDecimal("10.00"))))
                .containsExactly("distinctAccounts");
    }

    private static Set<String> invalidFields(TransferEventDTO dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private static TransferEventDTO transfer(String transactionId, UUID from, UUID to, BigDecimal amount) {
        TransferEventDTO dto = new TransferEventDTO();
        dto.setTransactionId(transactionId);
        dto.setFromAccountId(from);
        dto.setToAccountId(to);
        dto.setAmount(amount);
        return dto;
    }
}
