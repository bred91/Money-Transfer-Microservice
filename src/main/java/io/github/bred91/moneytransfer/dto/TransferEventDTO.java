package io.github.bred91.moneytransfer.dto;

import jakarta.validation.constraints.DecimalMin;
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

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be a positive value")
    private BigDecimal amount;
}