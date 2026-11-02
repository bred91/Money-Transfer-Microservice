package io.github.bred91.moneytransfer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Data required to open a new account")
public class CreateAccountRequestDTO {

    @Schema(description = "Owner first name", example = "Mario", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must be at most 255 characters")
    private String ownerName;

    @Schema(description = "Owner last name", example = "Rossi", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Surname is required")
    @Size(max = 255, message = "Surname must be at most 255 characters")
    private String ownerSurname;

    @Schema(description = "Initial balance, with at most 2 decimal places", example = "123.45",
            minimum = "0.00", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Initial balance is required")
    @DecimalMin(value = "0.00", message = "Initial balance must not be negative")
    @Digits(integer = 17, fraction = 2, message = "Initial balance must have at most 17 integer digits and 2 decimal places")
    private BigDecimal initialBalance;
}
