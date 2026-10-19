package org.example.navan.dao;

import org.example.navan.entity.InvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, Long> {
    boolean existsByTransactionId(Long transactionId);
}
