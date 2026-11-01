package io.github.bred91.moneytransfer.entity;

import jakarta.persistence.*;
import lombok.*;
import io.github.bred91.moneytransfer.enumeration.TransactionStatusEnum;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;
import java.time.Instant;

@Entity
@Table(name = "transfer_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferLogEntity {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "transaction_id", unique = true, nullable = false)
    private String transactionId;

    @Column(nullable = false)
    private UUID fromAccountId;

    @Column(nullable = false)
    private UUID toAccountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatusEnum status;

    private String failedReason;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}

