package org.example.navan.dao;

import jakarta.persistence.LockModeType;
import org.example.navan.entity.Invoice;
import org.example.navan.dto.InvoiceListItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    @Query("select new org.example.navan.dto.InvoiceListItem(" +
            "i.id, i.currency, i.grandTotal, i.createdAt) " +
            "from Invoice i where i.id > :afterId order by i.id asc")
    List<InvoiceListItem> findAfterId(@Param("afterId") Long afterId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> findForUpdate(@Param("id") Long id);
}
