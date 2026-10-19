package org.example.navan.dao;

import jakarta.persistence.LockModeType;
import org.example.navan.entity.Transaction;
import org.example.navan.dto.TransactionListItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByExternalId(String externalId);

    @Query("select new org.example.navan.dto.TransactionListItem(" +
            "t.id, t.externalId, t.type, t.currency, t.occurredAt, t.total) " +
            "from Transaction t where t.id > :afterId order by t.id asc")
    List<TransactionListItem> findAfterId(@Param("afterId") Long afterId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transaction t where t.id = :id")
    Optional<Transaction> findForUpdate(@Param("id") Long id);
}
