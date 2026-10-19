package org.example.navan.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Entity
@Table(name = "invoice_tax_rollup")
public class InvoiceTaxRollup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Setter
    @Column(nullable = false)
    private String name;

    @Setter
    @Column(precision = 9, scale = 6)
    private BigDecimal rate;

    @Setter
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    void setInvoice(Invoice invoice) {
        this.invoice = invoice;
    }
}
