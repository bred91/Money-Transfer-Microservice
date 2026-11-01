package io.github.bred91.moneytransfer.repository;

import io.github.bred91.moneytransfer.entity.TransferLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransferLogRepository extends JpaRepository<TransferLogEntity, Long> {
    Optional<TransferLogEntity> findByTransactionId(String transactionId);
}

