package org.example.navan.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

public record InvoiceListItem(
        Long id,
        String currency,
        @JsonProperty("grand_total") BigDecimal grandTotal,
        @JsonProperty("created_at") Instant createdAt
) {}
