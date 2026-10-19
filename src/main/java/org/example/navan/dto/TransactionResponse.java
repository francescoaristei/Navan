package org.example.navan.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TransactionResponse(
        Long id,
        @JsonProperty("external_id") String externalId,
        String type,
        String currency,
        @JsonProperty("occurred_at") Instant occurredAt,
        BigDecimal total,
        @JsonProperty("tax_lines") List<TransactionRequest.TaxLine> taxLines,
        @JsonProperty("fee_lines") List<TransactionRequest.FeeLine> feeLines,
        JsonNode metadata
) {}
