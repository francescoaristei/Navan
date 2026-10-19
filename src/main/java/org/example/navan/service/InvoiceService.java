package org.example.navan.service;

import org.example.navan.dao.InvoiceRepository;
import org.example.navan.dao.TransactionRepository;
import org.example.navan.dto.InvoiceRequest;
import org.example.navan.dto.InvoiceResponse;
import org.example.navan.dto.InvoiceListItem;
import org.example.navan.dto.InvoiceListResponse;
import org.example.navan.dto.TransactionRequest;
import org.example.navan.entity.Invoice;
import org.example.navan.entity.InvoiceFeeRollup;
import org.example.navan.entity.InvoiceLine;
import org.example.navan.entity.InvoiceTaxRollup;
import org.example.navan.entity.Transaction;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class InvoiceService {
    private final InvoiceRepository invoiceRepository;
    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;

    public InvoiceService(InvoiceRepository invoiceRepository, TransactionRepository transactionRepository,
                          ObjectMapper objectMapper) {
        this.invoiceRepository = invoiceRepository;
        this.transactionRepository = transactionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InvoiceResponse create(InvoiceRequest request) {
        List<Long> ids = request.transactionIds();
        validateIds(ids);
        Map<Long, Transaction> found = lockTransactions(ids);
        List<Transaction> transactions = ids.stream().map(found::get).toList();
        Invoice invoice = new Invoice();
        populateSnapshot(invoice, transactions);
        return toResponse(invoiceRepository.saveAndFlush(invoice));
    }

    @Transactional
    public InvoiceResponse patch(Long id, InvoiceRequest request) {
        Invoice invoice = invoiceRepository.findForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Invoice not found"));
        List<Long> requestedIds = request.transactionIds();
        validateIds(requestedIds);
        List<Long> affectedIds = new ArrayList<>(requestedIds);
        invoice.getLines().forEach(line -> affectedIds.add(line.getTransactionId()));
        Map<Long, Transaction> found = lockTransactions(affectedIds);
        List<Transaction> transactions = requestedIds.stream().map(found::get).toList();
        validateCurrencies(transactions);

        // Orphan removals must reach the database before inserting replacement
        // lines with the same (invoice_id, transaction_id) unique key.
        invoice.getLines().clear();
        invoice.getTaxRollup().clear();
        invoice.getFeeRollup().clear();
        invoiceRepository.flush();

        populateSnapshot(invoice, transactions);
        return toResponse(invoiceRepository.saveAndFlush(invoice));
    }

    @Transactional
    public void delete(Long id) {
        Invoice invoice = invoiceRepository.findForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Invoice not found"));
        lockTransactions(invoice.getLines().stream().map(InvoiceLine::getTransactionId).toList());
        invoiceRepository.delete(invoice);
        invoiceRepository.flush();
    }

    private void validateIds(List<Long> ids) {
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new ResponseStatusException(CONFLICT, "Duplicate transaction id");
        }
    }

    private Map<Long, Transaction> lockTransactions(List<Long> ids) {
        Map<Long, Transaction> found = new HashMap<>();
        ids.stream().distinct().sorted().forEach(id -> {
            Transaction transaction = transactionRepository.findForUpdate(id)
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Transaction not found: " + id));
            found.put(id, transaction);
        });
        return found;
    }

    private String validateCurrencies(List<Transaction> transactions) {
        String currency = transactions.get(0).getCurrency();
        if (transactions.stream().anyMatch(transaction -> !currency.equals(transaction.getCurrency()))) {
            throw new ResponseStatusException(CONFLICT, "Mixed currencies");
        }
        return currency;
    }

    private void populateSnapshot(Invoice invoice, List<Transaction> transactions) {
        invoice.setCurrency(validateCurrencies(transactions));
        BigDecimal grandTotal = BigDecimal.ZERO;
        Map<TaxKey, BigDecimal> taxes = new LinkedHashMap<>();
        Map<String, BigDecimal> fees = new LinkedHashMap<>();
        for (Transaction transaction : transactions) {
            grandTotal = grandTotal.add(transaction.getTotal());
            List<TransactionRequest.TaxLine> taxLines = transaction.getTaxLines().stream()
                    .map(line -> new TransactionRequest.TaxLine(line.getName(), line.getRate(), line.getAmount()))
                    .toList();
            List<TransactionRequest.FeeLine> feeLines = transaction.getFeeLines().stream()
                    .map(line -> new TransactionRequest.FeeLine(line.getName(), line.getAmount()))
                    .toList();

            InvoiceLine line = new InvoiceLine();
            line.setTransactionId(transaction.getId());
            line.setType(transaction.getType());
            line.setTotal(transaction.getTotal());
            line.setMetadataSnapshot(transaction.getMetadata());
            line.setTaxLinesSnapshot(objectMapper.writeValueAsString(taxLines));
            line.setFeeLinesSnapshot(objectMapper.writeValueAsString(feeLines));
            invoice.addLine(line);

            for (TransactionRequest.TaxLine tax : taxLines) {
                BigDecimal rate = tax.rate() == null ? null : tax.rate().stripTrailingZeros();
                taxes.merge(new TaxKey(tax.name(), rate), tax.amount(), BigDecimal::add);
            }
            for (TransactionRequest.FeeLine fee : feeLines) {
                fees.merge(fee.name(), fee.amount(), BigDecimal::add);
            }
        }

        invoice.setGrandTotal(grandTotal);
        taxes.forEach((key, amount) -> {
            InvoiceTaxRollup rollup = new InvoiceTaxRollup();
            rollup.setName(key.name());
            rollup.setRate(key.rate());
            rollup.setAmount(amount);
            invoice.addTaxRollup(rollup);
        });

        fees.forEach((name, amount) -> {
            InvoiceFeeRollup rollup = new InvoiceFeeRollup();
            rollup.setName(name);
            rollup.setAmount(amount);
            invoice.addFeeRollup(rollup);
        });
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getById(Long id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Invoice not found"));
        return toResponse(invoice);
    }

    @Transactional(readOnly = true)
    public InvoiceListResponse list(int limit, Long afterId) {
        if (limit < 1 || limit > 100) {
            throw new ResponseStatusException(BAD_REQUEST, "limit must be between 1 and 100");
        }
        if (afterId != null && afterId < 0) {
            throw new ResponseStatusException(BAD_REQUEST, "after_id must be non-negative");
        }

        List<InvoiceListItem> rows = invoiceRepository.findAfterId(
                afterId == null ? 0L : afterId, PageRequest.of(0, limit + 1));
        boolean hasMore = rows.size() > limit;
        List<InvoiceListItem> items = rows.stream().limit(limit).toList();
        Long nextCursor = hasMore ? items.get(items.size() - 1).id() : null;
        return new InvoiceListResponse(items, nextCursor);
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        List<InvoiceResponse.Line> lines = invoice.getLines().stream()
                .sorted(Comparator.comparing(InvoiceLine::getId))
                .map(line -> new InvoiceResponse.Line(
                        line.getTransactionId(), line.getType().name().toLowerCase(Locale.ROOT), line.getTotal(),
                        objectMapper.readTree(line.getMetadataSnapshot()),
                        objectMapper.readValue(line.getTaxLinesSnapshot(),
                                new TypeReference<List<TransactionRequest.TaxLine>>() {}),
                        objectMapper.readValue(line.getFeeLinesSnapshot(),
                                new TypeReference<List<TransactionRequest.FeeLine>>() {})))
                .toList();
        List<InvoiceResponse.TaxRollup> taxes = invoice.getTaxRollup().stream()
                .sorted(Comparator.comparing(InvoiceTaxRollup::getId))
                .map(tax -> new InvoiceResponse.TaxRollup(tax.getName(), tax.getRate(), tax.getAmount()))
                .toList();
        List<InvoiceResponse.FeeRollup> fees = invoice.getFeeRollup().stream()
                .sorted(Comparator.comparing(InvoiceFeeRollup::getId))
                .map(fee -> new InvoiceResponse.FeeRollup(fee.getName(), fee.getAmount()))
                .toList();
        return new InvoiceResponse(invoice.getId(), invoice.getCurrency(), invoice.getGrandTotal(),
                invoice.getCreatedAt(), lines, taxes, fees);
    }

    private record TaxKey(String name, BigDecimal rate) {}
}
