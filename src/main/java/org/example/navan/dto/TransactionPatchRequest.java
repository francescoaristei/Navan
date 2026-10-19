package org.example.navan.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import org.example.navan.dto.TransactionRequest.FeeLine;
import org.example.navan.dto.TransactionRequest.TaxLine;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record TransactionPatchRequest(
        @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal total,
        @JsonProperty("occurred_at") Instant occurredAt,
        @JsonProperty("tax_lines") List<@NotNull @Valid TaxLine> taxLines,
        @JsonProperty("fee_lines") List<@NotNull @Valid FeeLine> feeLines,
        JsonNode metadata
) {}
