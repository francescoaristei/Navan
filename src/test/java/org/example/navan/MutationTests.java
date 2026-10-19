package org.example.navan;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MutationTests {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ObjectMapper objectMapper;

    @Test
    void patchesAndDeletesAnUnbilledTransactionAtomically() throws Exception {
        long id = transaction("EUR", "20.00");
        mockMvc.perform(patch("/transactions/{id}", id).contentType("application/json").content("""
                {"total":55.00,"occurred_at":"2026-03-12T10:00:00Z",
                 "tax_lines":[{"name":"New tax","rate":0.10,"amount":5.00}],
                 "fee_lines":[],"metadata":{"fee_kind":"agent_call_fee"}}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(55.00))
                .andExpect(jsonPath("$.tax_lines[0].name").value("New tax"))
                .andExpect(jsonPath("$.fee_lines.length()").value(0))
                .andExpect(jsonPath("$.metadata.fee_kind").value("agent_call_fee"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transaction_tax_lines WHERE transaction_id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transaction_fee_lines WHERE transaction_id = ?", Integer.class, id)).isZero();
        mockMvc.perform(delete("/transactions/{id}", id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/transactions/{id}", id)).andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transaction_tax_lines WHERE transaction_id = ?", Integer.class, id)).isZero();
    }

    @Test
    void billedTransactionRejectsMutationUntilInvoiceIsDeleted() throws Exception {
        long id = transaction("EUR", "20.00");
        long invoiceId = invoice(id);

        mockMvc.perform(patch("/transactions/{id}", id).contentType("application/json")
                        .content("{\"total\":99.00}"))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/transactions/{id}", id)).andExpect(status().isConflict());
        mockMvc.perform(get("/transactions/{id}", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(20.00));
        mockMvc.perform(get("/invoices/{id}", invoiceId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.grand_total").value(20.00));

        mockMvc.perform(delete("/invoices/{id}", invoiceId)).andExpect(status().isNoContent());
        mockMvc.perform(get("/invoices/{id}", invoiceId)).andExpect(status().isNotFound());
        mockMvc.perform(patch("/transactions/{id}", id).contentType("application/json")
                        .content("{\"total\":99.00}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(99.00));
    }

    @Test
    void invoicePatchReplacesSnapshotAndPreservesItOnFailedValidation() throws Exception {
        long first = transaction("EUR", "20.00");
        long second = transaction("EUR", "30.00");
        long third = transaction("EUR", "40.00");
        long dollar = transaction("USD", "50.00");
        long invoiceId = invoice(first, second);

        mockMvc.perform(patch("/invoices/{id}", invoiceId).contentType("application/json")
                        .content("{\"transaction_ids\":[%d,%d]}".formatted(first, third)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grand_total").value(60.00))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.tax_rollup[0].amount").value(4.00))
                .andExpect(jsonPath("$.fee_rollup[0].amount").value(4.00));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM invoice_lines WHERE invoice_id = ?", Integer.class, invoiceId)).isEqualTo(2);

        mockMvc.perform(patch("/invoices/{id}", invoiceId).contentType("application/json")
                        .content("{\"transaction_ids\":[%d,%d]}".formatted(first, dollar)))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/invoices/{id}", invoiceId).contentType("application/json")
                        .content("{\"transaction_ids\":[%d,999999999]}".formatted(first)))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/invoices/{id}", invoiceId).contentType("application/json")
                        .content("{\"transaction_ids\":[%d,%d]}".formatted(first, first)))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/invoices/{id}", invoiceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grand_total").value(60.00))
                .andExpect(jsonPath("$.lines.length()").value(2));

        // The removed transaction is no longer billed by this invoice.
        mockMvc.perform(patch("/transactions/{id}", second).contentType("application/json")
                        .content("{\"total\":35.00}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/invoices/{id}", invoiceId)).andExpect(status().isNoContent());
        mockMvc.perform(get("/transactions/{id}", first)).andExpect(status().isOk());
        mockMvc.perform(get("/transactions/{id}", third)).andExpect(status().isOk());
    }

    @Test
    void overlappingPatchesLeaveOneCompleteTransactionState() throws Exception {
        long id = transaction("EUR", "20.00");
        String first = """
                {"total":11.00,"tax_lines":[{"name":"A","amount":1.00}],
                 "fee_lines":[{"name":"A","amount":1.00}]}
                """;
        String second = """
                {"total":22.00,"tax_lines":[{"name":"B","amount":2.00}],
                 "fee_lines":[{"name":"B","amount":2.00}]}
                """;
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> patchA = () -> patchAtOnce(id, first, ready, start);
            Callable<Integer> patchB = () -> patchAtOnce(id, second, ready, start);
            var a = executor.submit(patchA);
            var b = executor.submit(patchB);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(a.get(15, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(b.get(15, TimeUnit.SECONDS)).isEqualTo(200);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        JsonNode saved = objectMapper.readTree(mockMvc.perform(get("/transactions/{id}", id))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String expectedName = saved.path("total").decimalValue().intValue() == 11 ? "A" : "B";
        assertThat(saved.path("tax_lines").get(0).path("name").asText()).isEqualTo(expectedName);
        assertThat(saved.path("fee_lines").get(0).path("name").asText()).isEqualTo(expectedName);
    }

    @Test
    void invoiceCreateAndTransactionPatchHaveAConsistentOrder() throws Exception {
        long id = transaction("EUR", "20.00");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        String invoiceResponse;
        int patchStatus;
        try {
            var invoice = executor.submit(() -> {
                ready.countDown();
                start.await();
                var response = mockMvc.perform(post("/invoices").contentType("application/json")
                        .content("{\"transaction_ids\":[%d]}".formatted(id))).andReturn().getResponse();
                assertThat(response.getStatus()).isEqualTo(201);
                return response.getContentAsString();
            });
            var patch = executor.submit(() -> patchAtOnce(id, "{\"total\":99.00}", ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            invoiceResponse = invoice.get(15, TimeUnit.SECONDS);
            patchStatus = patch.get(15, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(patchStatus).isIn(200, 409);
        long invoiceId = objectMapper.readTree(invoiceResponse).path("id").asLong();
        JsonNode savedInvoice = objectMapper.readTree(mockMvc.perform(get("/invoices/{id}", invoiceId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        int expectedTotal = patchStatus == 200 ? 99 : 20;
        assertThat(savedInvoice.path("grand_total").decimalValue().intValue()).isEqualTo(expectedTotal);
        assertThat(savedInvoice.path("lines").get(0).path("total").decimalValue().intValue())
                .isEqualTo(expectedTotal);
    }

    private int patchAtOnce(long id, String body, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return mockMvc.perform(patch("/transactions/{id}", id).contentType("application/json").content(body))
                .andReturn().getResponse().getStatus();
    }

    private long transaction(String currency, String total) throws Exception {
        String body = """
                {"type":"navan_fee","external_id":"%s","currency":"%s",
                 "occurred_at":"2026-03-11T10:00:00Z","total":%s,
                 "tax_lines":[{"name":"VAT","rate":0.07,"amount":2.00}],
                 "fee_lines":[{"name":"YQ","amount":2.00}],
                 "metadata":{"fee_kind":"trip_fee"}}
                """.formatted(UUID.randomUUID(), currency, total);
        String response = mockMvc.perform(post("/transactions").contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("id").asLong();
    }

    private long invoice(long... ids) throws Exception {
        StringBuilder body = new StringBuilder("{\"transaction_ids\":[");
        for (int index = 0; index < ids.length; index++) {
            if (index > 0) body.append(',');
            body.append(ids[index]);
        }
        body.append("]}");
        String response = mockMvc.perform(post("/invoices").contentType("application/json").content(body.toString()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("id").asLong();
    }
}
