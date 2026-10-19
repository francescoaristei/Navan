package org.example.navan;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InvoiceListTests {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void followsIdCursorAndReturnsOnlyInvoiceHeaders() throws Exception {
        long transactionId = createTransaction();
        long first = createInvoice(transactionId);
        long second = createInvoice(transactionId);
        long third = createInvoice(transactionId);

        String firstPage = mockMvc.perform(get("/invoices")
                        .param("limit", "1").param("after_id", Long.toString(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(second))
                .andExpect(jsonPath("$.items[0].currency").value("EUR"))
                .andExpect(jsonPath("$.items[0].grand_total").value(20.00))
                .andExpect(jsonPath("$.items[0].created_at").exists())
                .andExpect(jsonPath("$.items[0].lines").doesNotExist())
                .andExpect(jsonPath("$.items[0].tax_rollup").doesNotExist())
                .andExpect(jsonPath("$.next_cursor").value(second))
                .andReturn().getResponse().getContentAsString();

        long cursor = objectMapper.readTree(firstPage).path("next_cursor").asLong();
        mockMvc.perform(get("/invoices")
                        .param("limit", "1").param("after_id", Long.toString(cursor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(third))
                .andExpect(jsonPath("$.next_cursor").value((Object) null));
    }

    @Test
    void defaultsToFiftyAndRejectsInvalidPaginationParameters() throws Exception {
        mockMvc.perform(get("/invoices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(org.hamcrest.Matchers.lessThanOrEqualTo(50)));
        mockMvc.perform(get("/invoices").param("limit", "1000"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/invoices").param("limit", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/invoices").param("after_id", "-1"))
                .andExpect(status().isBadRequest());
    }

    private long createTransaction() throws Exception {
        String body = """
                {"type":"navan_fee","external_id":"%s","currency":"EUR",
                 "occurred_at":"2026-03-11T10:00:00Z","total":20.00,
                 "tax_lines":[],"fee_lines":[],"metadata":{"fee_kind":"trip_fee"}}
                """.formatted(UUID.randomUUID());
        String response = mockMvc.perform(post("/transactions").contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("id").asLong();
    }

    private long createInvoice(long transactionId) throws Exception {
        String body = "{\"transaction_ids\":[%d]}".formatted(transactionId);
        String response = mockMvc.perform(post("/invoices").contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(response).path("id").asLong();
        assertThat(id).isPositive();
        return id;
    }
}
