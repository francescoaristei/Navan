package org.example.navan.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record InvoiceResponse(
        Long id,
        String currency,
        @JsonProperty("grand_total") BigDecimal grandTotal,
        @JsonProperty("created_at") Instant createdAt,
        List<Line> lines,
        @JsonProperty("tax_rollup") List<TaxRollup> taxRollup,
        @JsonProperty("fee_rollup") List<FeeRollup> feeRollup
) {
    public record Line(
            @JsonProperty("transaction_id") Long transactionId,
            String type,
            BigDecimal total,
            JsonNode metadata,
            @JsonProperty("tax_lines") List<TransactionRequest.TaxLine> taxLines,
            @JsonProperty("fee_lines") List<TransactionRequest.FeeLine> feeLines
    ) {}

    public record TaxRollup(String name, BigDecimal rate, BigDecimal amount) {}

    public record FeeRollup(String name, BigDecimal amount) {}
}
