package org.example.navan.service;

import org.example.navan.dao.TransactionRepository;
import org.example.navan.dao.InvoiceLineRepository;
import org.example.navan.dto.TransactionPatchRequest;
import org.example.navan.dto.TransactionRequest;
import org.example.navan.dto.TransactionResponse;
import org.example.navan.dto.TransactionListResponse;
import org.example.navan.dto.TransactionListItem;
import org.example.navan.entity.Transaction;
import org.example.navan.entity.TransactionFeeLine;
import org.example.navan.entity.TransactionTaxLine;
import org.example.navan.entity.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.List;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class TransactionService {
    private final TransactionRepository repository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final ObjectMapper objectMapper;

    public TransactionService(TransactionRepository repository, InvoiceLineRepository invoiceLineRepository,
                              ObjectMapper objectMapper) {
        this.repository = repository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TransactionResponse create(TransactionRequest request) {
        TransactionType type;
        try {
            type = TransactionType.valueOf(request.type().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Unknown transaction type");
        }
        validateMetadata(type, request.metadata());

        Transaction transaction = new Transaction();
        transaction.setExternalId(request.externalId());
        transaction.setType(type);
        transaction.setCurrency(request.currency());
        transaction.setOccurredAt(request.occurredAt());
        transaction.setTotal(request.total());
        transaction.setMetadata(request.metadata().toString());

        for (TransactionRequest.TaxLine line : request.taxLines()) {
            TransactionTaxLine taxLine = new TransactionTaxLine();
            taxLine.setName(line.name());
            taxLine.setRate(line.rate());
            taxLine.setAmount(line.amount());
            transaction.addTaxLine(taxLine);
        }
        for (TransactionRequest.FeeLine line : request.feeLines()) {
            TransactionFeeLine feeLine = new TransactionFeeLine();
            feeLine.setName(line.name());
            feeLine.setAmount(line.amount());
            transaction.addFeeLine(feeLine);
        }

        return toResponse(repository.saveAndFlush(transaction));
    }

    @Transactional(readOnly = true)
    public TransactionResponse getById(Long id) {
        Transaction transaction = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Transaction not found"));
        return toResponse(transaction);
    }

    @Transactional(readOnly = true)
    public TransactionListResponse list(int limit, Long afterId) {
        if (limit < 1 || limit > 100) {
            throw new ResponseStatusException(BAD_REQUEST, "limit must be between 1 and 100");
        }
        if (afterId != null && afterId < 0) {
            throw new ResponseStatusException(BAD_REQUEST, "after_id must be non-negative");
        }

        List<TransactionListItem> rows = repository.findAfterId(
                afterId == null ? 0L : afterId, PageRequest.of(0, limit + 1));
        boolean hasMore = rows.size() > limit;
        List<TransactionListItem> items = rows.stream().limit(limit).toList();
        Long nextCursor = hasMore ? items.get(items.size() - 1).id() : null;
        return new TransactionListResponse(items, nextCursor);
    }

    @Transactional
    public TransactionResponse patch(Long id, TransactionPatchRequest request) {
        Transaction transaction = lockUnbilled(id);
        if (request.metadata() != null) {
            validateMetadata(transaction.getType(), request.metadata());
            transaction.setMetadata(request.metadata().toString());
        }
        if (request.total() != null) {
            transaction.setTotal(request.total());
        }
        if (request.occurredAt() != null) {
            transaction.setOccurredAt(request.occurredAt());
        }
        if (request.taxLines() != null) {
            transaction.getTaxLines().clear();
            for (TransactionRequest.TaxLine line : request.taxLines()) {
                TransactionTaxLine taxLine = new TransactionTaxLine();
                taxLine.setName(line.name());
                taxLine.setRate(line.rate());
                taxLine.setAmount(line.amount());
                transaction.addTaxLine(taxLine);
            }
        }
        if (request.feeLines() != null) {
            transaction.getFeeLines().clear();
            for (TransactionRequest.FeeLine line : request.feeLines()) {
                TransactionFeeLine feeLine = new TransactionFeeLine();
                feeLine.setName(line.name());
                feeLine.setAmount(line.amount());
                transaction.addFeeLine(feeLine);
            }
        }
        repository.flush();
        return toResponse(transaction);
    }

    @Transactional
    public void delete(Long id) {
        Transaction transaction = lockUnbilled(id);
        repository.delete(transaction);
        repository.flush();
    }

    private Transaction lockUnbilled(Long id) {
        Transaction transaction = repository.findForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Transaction not found"));
        if (invoiceLineRepository.existsByTransactionId(id)) {
            throw new ResponseStatusException(CONFLICT, "Transaction is on an invoice");
        }
        return transaction;
    }

    private TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(), transaction.getExternalId(),
                transaction.getType().name().toLowerCase(Locale.ROOT),
                transaction.getCurrency(), transaction.getOccurredAt(), transaction.getTotal(),
                transaction.getTaxLines().stream()
                        .map(line -> new TransactionRequest.TaxLine(line.getName(), line.getRate(), line.getAmount()))
                        .toList(),
                transaction.getFeeLines().stream()
                        .map(line -> new TransactionRequest.FeeLine(line.getName(), line.getAmount()))
                        .toList(),
                objectMapper.readTree(transaction.getMetadata())
        );
    }

    private void validateMetadata(TransactionType type, JsonNode metadata) {
        if (!metadata.isObject() || metadata.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "metadata must be a nonempty object");
        }
        switch (type) {
            case FLIGHT -> {
                requireText(metadata, "origin", "destination");
                requireNumber(metadata, "fare");
                for (JsonNode coupon : requireArray(metadata, "coupons")) {
                    requireText(coupon, "flight_number", "from", "to", "departure", "arrival");
                }
            }
            case HOTEL -> {
                requireText(metadata, "name", "check_in", "check_out");
                requireNumber(metadata, "nights", "room_rate_per_night");
                JsonNode address = metadata.path("address");
                requireText(address, "street", "city", "country");
            }
            case RAIL -> {
                for (JsonNode leg : requireArray(metadata, "legs")) {
                    requireText(leg, "origin", "destination", "departure", "arrival");
                }
            }
            case NAVAN_FEE -> {
                String feeKind = metadata.path("fee_kind").asText();
                if (!feeKind.equals("trip_fee") && !feeKind.equals("agent_call_fee")) {
                    throw new ResponseStatusException(BAD_REQUEST, "Unknown navan_fee fee_kind");
                }
            }
        }
    }

    private void requireText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isTextual() || value.asText().isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "metadata." + field + " is required");
            }
        }
    }

    private void requireNumber(JsonNode node, String... fields) {
        for (String field : fields) {
            if (!node.path(field).isNumber()) {
                throw new ResponseStatusException(BAD_REQUEST, "metadata." + field + " must be a number");
            }
        }
    }

    private JsonNode requireArray(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isArray() || value.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "metadata." + field + " must be a nonempty array");
        }
        return value;
    }
}
