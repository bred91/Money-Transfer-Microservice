package io.github.bred91.moneytransfer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateAccountRequestDTO {
    @NotBlank(message = "Name is required")
    private String ownerName;
    @NotBlank(message = "Surname is required")
    private String ownerSurname;
    @NotNull(message = "Initial balance is required")
    @DecimalMin(value = "0.00", message = "Initial balance must be a positive value")
    private BigDecimal initialBalance;
}
