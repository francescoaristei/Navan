package org.example.navan.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "invoices")
public class Invoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false, length = 3)
    private String currency;

    @Setter
    @Column(name = "grand_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal grandTotal;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceTaxRollup> taxRollup = new ArrayList<>();

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceFeeRollup> feeRollup = new ArrayList<>();

    public void addLine(InvoiceLine line) {
        line.setInvoice(this);
        lines.add(line);
    }

    public void addTaxRollup(InvoiceTaxRollup tax) {
        tax.setInvoice(this);
        taxRollup.add(tax);
    }

    public void addFeeRollup(InvoiceFeeRollup fee) {
        fee.setInvoice(this);
        feeRollup.add(fee);
    }
}
