package org.example.navan.controller;

import jakarta.validation.Valid;
import org.example.navan.dao.TransactionRepository;
import org.example.navan.dto.TransactionRequest;
import org.example.navan.dto.TransactionPatchRequest;
import org.example.navan.dto.TransactionResponse;
import org.example.navan.dto.TransactionListResponse;
import org.example.navan.service.TransactionService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/transactions")
public class TransactionController {
    private final TransactionService service;
    private final TransactionRepository repository;

    public TransactionController(TransactionService service, TransactionRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody TransactionRequest request) {
        try {
            TransactionResponse response = service.create(request);
            return ResponseEntity.created(URI.create("/transactions/" + response.id())).body(response);
        } catch (DataIntegrityViolationException exception) {
            return repository.findByExternalId(request.externalId())
                    .<ResponseEntity<?>>map(existing -> ResponseEntity.status(409).body(Map.of("id", existing.getId())))
                    .orElseThrow(() -> exception);
        }
    }

    @GetMapping("/{id}")
    public TransactionResponse getById(@PathVariable("id") Long id) {
        return service.getById(id);
    }

    @GetMapping
    public TransactionListResponse list(@RequestParam(name = "limit", defaultValue = "50") int limit,
                                        @RequestParam(name = "after_id", required = false) Long afterId) {
        return service.list(limit, afterId);
    }

    @PatchMapping("/{id}")
    public TransactionResponse patch(@PathVariable("id") Long id, @Valid @RequestBody TransactionPatchRequest request) {
        return service.patch(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
