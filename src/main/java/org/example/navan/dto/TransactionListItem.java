package org.example.navan.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.example.navan.entity.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

public record TransactionListItem(
        Long id,
        @JsonProperty("external_id") String externalId,
        String type,
        String currency,
        @JsonProperty("occurred_at") Instant occurredAt,
        BigDecimal total
) {
    public TransactionListItem(Long id, String externalId, TransactionType type,
                               String currency, Instant occurredAt, BigDecimal total) {
        this(id, externalId, type.name().toLowerCase(Locale.ROOT), currency, occurredAt, total);
    }
}
