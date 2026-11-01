package io.github.bred91.moneytransfer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransferEventDTO {

    @NotBlank(message = "Transaction ID is required")
    private String transactionId;

    @NotNull(message = "Source account is required")
    @Positive(message = "Source account must be positive")
    private Long fromAccountId;

    @NotNull(message = "Destination account is required")
    @Positive(message = "Destination account must be positive")
    private Long toAccountId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be a positive value")
    private BigDecimal amount;
}