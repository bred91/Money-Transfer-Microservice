package io.github.bred91.moneytransfer.dto;

import lombok.Builder;
import lombok.Data;
import io.github.bred91.moneytransfer.enumeration.AccountStatusEnum;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class AccountResponseDTO {
    private Long id;
    private String ownerName;
    private String ownerSurname;
    private AccountStatusEnum status;
    private BigDecimal balance;
    private LocalDateTime createdAt;
    private LocalDateTime lastModifiedAt;
}
