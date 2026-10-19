package org.example.navan.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Entity
@Table(name = "invoice_lines")
public class InvoiceLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Setter
    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Setter
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal total;

    @Setter
    @Column(name = "metadata_snapshot", nullable = false, columnDefinition = "TEXT")
    private String metadataSnapshot;

    @Setter
    @Column(name = "tax_lines_snapshot", nullable = false, columnDefinition = "TEXT")
    private String taxLinesSnapshot;

    @Setter
    @Column(name = "fee_lines_snapshot", nullable = false, columnDefinition = "TEXT")
    private String feeLinesSnapshot;

    void setInvoice(Invoice invoice) {
        this.invoice = invoice;
    }
}
