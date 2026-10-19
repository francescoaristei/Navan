package org.example.navan.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TransactionRequest(
        @NotBlank String type,
        @JsonProperty("external_id") @NotBlank String externalId,
        @NotBlank String currency,
        @JsonProperty("occurred_at") @NotNull Instant occurredAt,
        @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal total,
        @JsonProperty("tax_lines") @NotNull List<@NotNull @Valid TaxLine> taxLines,
        @JsonProperty("fee_lines") @NotNull List<@NotNull @Valid FeeLine> feeLines,
        @NotNull JsonNode metadata
) {
    public record TaxLine(
            @NotBlank String name,
            @Digits(integer = 3, fraction = 6) BigDecimal rate,
            @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal amount
    ) {}

    public record FeeLine(
            @NotBlank String name,
            @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal amount
    ) {}
}
