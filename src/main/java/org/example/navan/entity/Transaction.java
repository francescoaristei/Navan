package org.example.navan.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "transactions")
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Setter
    @Column(nullable = false, length = 3)
    private String currency;

    @Setter
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Setter
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal total;

    @Setter
    @Column(nullable = false, columnDefinition = "TEXT")
    private String metadata;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TransactionTaxLine> taxLines = new ArrayList<>();

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TransactionFeeLine> feeLines = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void addTaxLine(TransactionTaxLine taxLine) {
        taxLine.setTransaction(this);
        taxLines.add(taxLine);
    }

    public void addFeeLine(TransactionFeeLine feeLine) {
        feeLine.setTransaction(this);
        feeLines.add(feeLine);
    }
}
