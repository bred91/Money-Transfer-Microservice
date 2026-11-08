package io.github.bred91.moneytransfer.dto;

import io.github.bred91.moneytransfer.enumeration.AccountStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@Schema(description = "An account and its current balance")
public class AccountResponseDTO {

    @Schema(description = "Account ID (UUIDv7)", example = "019a3f6e-1c2b-7d4e-9a10-3b5c7d9e1f20")
    private UUID id;

    @Schema(description = "Owner first name", example = "Mario")
    private String ownerName;

    @Schema(description = "Owner last name", example = "Rossi")
    private String ownerSurname;

    @Schema(description = "Account status; only `ACTIVE` accounts can send or receive transfers", example = "ACTIVE")
    private AccountStatusEnum status;

    @Schema(description = "Current balance", example = "123.45")
    private BigDecimal balance;

    @Schema(description = "Creation timestamp (ISO 8601, UTC)", example = "2026-11-07T17:55:50.123456Z")
    private Instant createdAt;

    @Schema(description = "Last modification timestamp (ISO 8601, UTC)", example = "2026-11-07T17:55:50.123456Z")
    private Instant lastModifiedAt;
}
