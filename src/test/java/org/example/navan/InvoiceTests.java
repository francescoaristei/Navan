package org.example.navan;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
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
class InvoiceTests {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ObjectMapper objectMapper;

    @Test
    void createsSnapshotAndRollupsFromMultipleTransactions() throws Exception {
        long first = transaction("EUR", "100.00", "0.07", "7.00", "2.00");
        long second = transaction("EUR", "50.00", "0.07", "3.50", "1.00");
        String body = "{\"transaction_ids\":[%d,%d]}".formatted(first, second);

        String response = mockMvc.perform(post("/invoices").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.grand_total").value(150.00))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[0].transaction_id").value(first))
                .andExpect(jsonPath("$.lines[0].metadata.fee_kind").value("trip_fee"))
                .andExpect(jsonPath("$.lines[0].tax_lines[0].name").value("VAT"))
                .andExpect(jsonPath("$.tax_rollup[0].name").value("VAT"))
                .andExpect(jsonPath("$.tax_rollup[0].amount").value(10.50))
                .andExpect(jsonPath("$.fee_rollup[0].name").value("YQ"))
                .andExpect(jsonPath("$.fee_rollup[0].amount").value(3.00))
                .andReturn().getResponse().getContentAsString();

        long invoiceId = objectMapper.readTree(response).path("id").asLong();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM invoice_lines WHERE invoice_id = ?", Integer.class, invoiceId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM invoice_tax_rollup WHERE invoice_id = ?", Integer.class, invoiceId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM invoice_fee_rollup WHERE invoice_id = ?", Integer.class, invoiceId)).isEqualTo(1);

        String snapshot = jdbcTemplate.queryForObject(
                "SELECT tax_lines_snapshot FROM invoice_lines WHERE invoice_id = ? AND transaction_id = ?",
                String.class, invoiceId, first);
        jdbcTemplate.update("UPDATE transaction_tax_lines SET amount = 99.00 WHERE transaction_id = ?", first);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT tax_lines_snapshot FROM invoice_lines WHERE invoice_id = ? AND transaction_id = ?",
                String.class, invoiceId, first)).isEqualTo(snapshot);

        jdbcTemplate.update("UPDATE transactions SET total = 999.00, metadata = ? WHERE id = ?",
                "{\"fee_kind\":\"agent_call_fee\"}", first);
        mockMvc.perform(get("/invoices/{id}", invoiceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grand_total").value(150.00))
                .andExpect(jsonPath("$.lines[0].total").value(100.00))
                .andExpect(jsonPath("$.lines[0].metadata.fee_kind").value("trip_fee"))
                .andExpect(jsonPath("$.lines[0].tax_lines[0].amount").value(7.00))
                .andExpect(jsonPath("$.tax_rollup[0].amount").value(10.50))
                .andExpect(jsonPath("$.fee_rollup[0].amount").value(3.00));
        mockMvc.perform(get("/transactions/{id}", first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(999.00))
                .andExpect(jsonPath("$.metadata.fee_kind").value("agent_call_fee"))
                .andExpect(jsonPath("$.tax_lines[0].amount").value(99.00))
                .andExpect(jsonPath("$.fee_lines[0].name").value("YQ"));
    }

    @Test
    void rejectsDuplicatesMissingIdsAndMixedCurrenciesWithoutSavingInvoices() throws Exception {
        long euro = transaction("EUR", "100.00", "0.07", "7.00", "2.00");
        long dollar = transaction("USD", "50.00", "0.07", "3.50", "1.00");
        int before = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM invoices", Integer.class);

        mockMvc.perform(post("/invoices").contentType("application/json")
                        .content("{\"transaction_ids\":[%d,%d]}".formatted(euro, euro)))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/invoices").contentType("application/json")
                        .content("{\"transaction_ids\":[%d,999999999]}".formatted(euro)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/invoices").contentType("application/json")
                        .content("{\"transaction_ids\":[%d,%d]}".formatted(euro, dollar)))
                .andExpect(status().isConflict());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM invoices", Integer.class)).isEqualTo(before);
    }

    @Test
    void keepsDifferentTaxRatesInSeparateRollupEntries() throws Exception {
        long first = transaction("EUR", "100.00", "0.07", "7.00", "2.00");
        long second = transaction("EUR", "50.00", "0.10", "5.00", "1.00");

        mockMvc.perform(post("/invoices").contentType("application/json")
                        .content("{\"transaction_ids\":[%d,%d]}".formatted(first, second)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tax_rollup.length()").value(2))
                .andExpect(jsonPath("$.tax_rollup[0].amount").value(7.00))
                .andExpect(jsonPath("$.tax_rollup[1].amount").value(5.00));
    }

    @Test
    void getMissingTransactionAndInvoiceReturnsNotFound() throws Exception {
        mockMvc.perform(get("/transactions/{id}", 999999999L)).andExpect(status().isNotFound());
        mockMvc.perform(get("/invoices/{id}", 999999999L)).andExpect(status().isNotFound());
    }

    private long transaction(String currency, String total, String rate, String tax, String fee) throws Exception {
        String body = """
                {"type":"navan_fee","external_id":"%s","currency":"%s",
                 "occurred_at":"2026-03-11T10:00:00Z","total":%s,
                 "tax_lines":[{"name":"VAT","rate":%s,"amount":%s}],
                 "fee_lines":[{"name":"YQ","amount":%s}],
                 "metadata":{"fee_kind":"trip_fee"}}
                """.formatted(UUID.randomUUID(), currency, total, rate, tax, fee);
        String response = mockMvc.perform(post("/transactions").contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("id").asLong();
    }
}
