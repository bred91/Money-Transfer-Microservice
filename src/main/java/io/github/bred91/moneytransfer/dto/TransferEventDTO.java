package io.github.bred91.moneytransfer.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class TransferEventDTO {

    @NotBlank(message = "Transaction ID is required")
    private String transactionId;

    @NotNull(message = "Source account is required")
    private UUID fromAccountId;

    @NotNull(message = "Destination account is required")
    private UUID toAccountId;

    // Must fit the NUMERIC(19, 2) balance columns: an amount with more decimals would be rounded by the database
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be a positive value")
    @Digits(integer = 17, fraction = 2, message = "Amount must have at most 17 integer digits and 2 decimal places")
    private BigDecimal amount;

    @JsonIgnore
    @AssertTrue(message = "Source and destination accounts must be different")
    public boolean isDistinctAccounts() {
        return fromAccountId == null || !fromAccountId.equals(toAccountId);
    }
}
