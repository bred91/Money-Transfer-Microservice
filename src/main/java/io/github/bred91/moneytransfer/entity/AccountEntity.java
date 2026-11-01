package io.github.bred91.moneytransfer.entity;

import jakarta.persistence.*;
import lombok.*;
import io.github.bred91.moneytransfer.enumeration.AccountStatusEnum;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.util.UUID;
import java.time.LocalDateTime;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountEntity {
    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false)
    private String ownerName;

    @Column(nullable = false)
    private String ownerSurname;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatusEnum status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime lastModifiedAt;
}
