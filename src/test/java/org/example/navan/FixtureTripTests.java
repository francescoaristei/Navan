package org.example.navan;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class FixtureTripTests {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ObjectMapper objectMapper;

    @Test
    void seedTripRoundTripsAndInvoiceKeepsTheFlightSnapshot() throws Exception {
        JsonNode seeds = readResource("seed-payloads.json");
        JsonNode expected = readResource("expected-invoice.json");
        String[] kinds = {"flight", "hotel", "rail", "trip_fee", "agent_call_fee"};
        String runSuffix = "-" + UUID.randomUUID();
        Map<String, Long> ids = new LinkedHashMap<>();
        Map<String, JsonNode> storedMetadata = new LinkedHashMap<>();

        for (String kind : kinds) {
            JsonNode seed = seeds.path(kind);
            String body = seed.toString();
            for (String referencedKind : kinds) {
                String externalId = seeds.path(referencedKind).path("external_id").asText();
                body = body.replace(externalId, externalId + runSuffix);
            }
            JsonNode posted = objectMapper.readTree(body);
            JsonNode created = response(post("/transactions").contentType("application/json").content(body), 201);
            long id = created.path("id").asLong();
            ids.put(kind, id);
            storedMetadata.put(kind, posted.path("metadata"));

            JsonNode stored = response(get("/transactions/{id}", id), 200);
            assertThat(stored.path("type").asText()).isEqualTo(seed.path("type").asText());
            assertThat(stored.path("external_id").asText()).isEqualTo(seed.path("external_id").asText() + runSuffix);
            assertThat(stored.path("metadata")).isEqualTo(posted.path("metadata"));
            assertThat(stored.path("metadata").isEmpty()).isFalse();
        }

        assertThat(response(get("/transactions/{id}", ids.get("flight")), 200)
                .path("metadata").path("coupons").get(0).path("flight_number").asText()).isEqualTo("BA982");
        assertThat(response(get("/transactions/{id}", ids.get("hotel")), 200)
                .path("metadata").path("address").path("street").asText()).isEqualTo("12 Kingsway");
        assertThat(response(get("/transactions/{id}", ids.get("rail")), 200)
                .path("metadata").path("legs").size()).isEqualTo(2);
        assertThat(response(get("/transactions/{id}", ids.get("trip_fee")), 200)
                .path("metadata").path("fee_kind").asText()).isEqualTo("trip_fee");
        assertThat(response(get("/transactions/{id}", ids.get("agent_call_fee")), 200)
                .path("metadata").path("fee_kind").asText()).isEqualTo("agent_call_fee");

        String invoiceBody = "{\"transaction_ids\":[%d,%d,%d,%d,%d]}".formatted(
                ids.get("flight"), ids.get("hotel"), ids.get("rail"), ids.get("trip_fee"), ids.get("agent_call_fee"));
        JsonNode createdInvoice = response(post("/invoices").contentType("application/json").content(invoiceBody), 201);
        long invoiceId = createdInvoice.path("id").asLong();
        JsonNode invoice = response(get("/invoices/{id}", invoiceId), 200);
        assertThat(invoice.path("currency").asText()).isEqualTo(expected.path("currency").asText());
        assertThat(invoice.path("lines").size()).isEqualTo(expected.path("line_count_minimum").asInt());
        assertThat(invoice.path("grand_total").decimalValue())
                .isEqualByComparingTo(expected.path("totals").path("grand_total").decimalValue());
        for (int index = 0; index < kinds.length; index++) {
            JsonNode line = invoice.path("lines").get(index);
            assertThat(line.path("transaction_id").asLong()).isEqualTo(ids.get(kinds[index]));
            assertThat(line.path("total").decimalValue())
                    .isEqualByComparingTo(expected.path("totals").path(kinds[index]).decimalValue());
            assertThat(line.path("metadata")).isEqualTo(storedMetadata.get(kinds[index]));
        }
        for (JsonNode tax : expected.path("tax_rollup_example")) {
            JsonNode saved = namedLine(invoice.path("tax_rollup"), tax.path("name").asText());
            assertThat(saved.path("amount").decimalValue()).isEqualByComparingTo(tax.path("amount").decimalValue());
            if (tax.has("rate")) {
                assertThat(saved.path("rate").decimalValue()).isEqualByComparingTo(tax.path("rate").decimalValue());
            }
        }
        for (JsonNode fee : expected.path("fee_rollup_example")) {
            JsonNode saved = namedLine(invoice.path("fee_rollup"), fee.path("name").asText());
            assertThat(saved.path("amount").decimalValue()).isEqualByComparingTo(fee.path("amount").decimalValue());
        }

        jdbcTemplate.update("UPDATE transactions SET total = 999.00 WHERE id = ?", ids.get("flight"));
        JsonNode changedFlight = response(get("/transactions/{id}", ids.get("flight")), 200);
        assertThat(changedFlight.path("total").decimalValue()).isEqualByComparingTo("999.00");
        JsonNode savedInvoice = response(get("/invoices/{id}", invoiceId), 200);
        assertThat(savedInvoice.path("grand_total").decimalValue()).isEqualByComparingTo("685.70");
        assertThat(savedInvoice.path("lines").get(0).path("total").decimalValue())
                .isEqualByComparingTo("220.00");
    }

    private JsonNode namedLine(JsonNode lines, String name) {
        for (JsonNode line : lines) {
            if (name.equals(line.path("name").asText())) return line;
        }
        throw new AssertionError("Missing rollup: " + name);
    }

    private JsonNode readResource(String name) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/" + name)) {
            if (stream == null) throw new IOException("Missing test resource: " + name);
            return objectMapper.readTree(stream);
        }
    }

    private JsonNode response(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                              int status) throws Exception {
        String body = mockMvc.perform(request)
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is(status))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }
}
