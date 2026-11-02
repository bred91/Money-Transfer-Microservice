package io.github.bred91.moneytransfer.repository;

import jakarta.persistence.LockModeType;
import io.github.bred91.moneytransfer.entity.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccountEntity a where a.id in :ids order by a.id asc")
    List<AccountEntity> findAllByIdForUpdateOrdered(@Param("ids") Collection<UUID> ids);
}

