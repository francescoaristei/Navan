package org.example.navan.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record TransactionListResponse(
        List<TransactionListItem> items,
        @JsonProperty("next_cursor") Long nextCursor
) {}
