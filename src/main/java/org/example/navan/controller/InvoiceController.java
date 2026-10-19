package org.example.navan.controller;

import jakarta.validation.Valid;
import org.example.navan.dto.InvoiceRequest;
import org.example.navan.dto.InvoiceResponse;
import org.example.navan.dto.InvoiceListResponse;
import org.example.navan.service.InvoiceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/invoices")
public class InvoiceController {
    private final InvoiceService service;

    public InvoiceController(InvoiceService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<InvoiceResponse> createInvoice(@Valid @RequestBody InvoiceRequest request) {
        InvoiceResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/invoices/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public InvoiceResponse getById(@PathVariable("id") Long id) {
        return service.getById(id);
    }

    @GetMapping
    public InvoiceListResponse list(@RequestParam(name = "limit", defaultValue = "50") int limit,
                                    @RequestParam(name = "after_id", required = false) Long afterId) {
        return service.list(limit, afterId);
    }

    @PatchMapping("/{id}")
    public InvoiceResponse patch(@PathVariable("id") Long id, @Valid @RequestBody InvoiceRequest request) {
        return service.patch(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
