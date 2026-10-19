package org.example.navan;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionTests {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ObjectMapper objectMapper;

    @Test
    void createsTransactionWithTaxFeeAndMetadata() throws Exception {
        String externalId = UUID.randomUUID().toString();
        String body = payload(externalId, "flight", """
                {"origin":"TXL","destination":"LHR","fare":180.00,
                 "coupons":[{"flight_number":"AB123","from":"TXL","to":"LHR",
                             "departure":"2026-03-11T10:00:00Z","arrival":"2026-03-11T12:00:00Z"}]}
                """);

        String response = mockMvc.perform(post("/transactions").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.external_id").value(externalId))
                .andExpect(jsonPath("$.type").value("flight"))
                .andExpect(jsonPath("$.tax_lines[0].name").value("Airport tax"))
                .andExpect(jsonPath("$.fee_lines[0].name").value("YQ"))
                .andExpect(jsonPath("$.metadata.coupons[0].flight_number").value("AB123"))
                .andReturn().getResponse().getContentAsString();

        long id = objectMapper.readTree(response).path("id").asLong();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transactions WHERE id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transaction_tax_lines WHERE transaction_id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transaction_fee_lines WHERE transaction_id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void duplicateExternalIdReturnsExistingIdWithoutAnotherRow() throws Exception {
        String externalId = UUID.randomUUID().toString();
        String body = payload(externalId, "navan_fee", "{\"fee_kind\":\"trip_fee\"}");

        String first = mockMvc.perform(post("/transactions").contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(first).path("id").asLong();

        mockMvc.perform(post("/transactions").contentType("application/json").content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.id").value(id));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transactions WHERE external_id = ?", Integer.class, externalId)).isEqualTo(1);
    }

    @Test
    void concurrentDuplicateCreatesStoreOneTransaction() throws Exception {
        String externalId = UUID.randomUUID().toString();
        String body = payload(externalId, "navan_fee", "{\"fee_kind\":\"agent_call_fee\"}");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> postTransaction = () -> {
            ready.countDown();
            start.await();
            return mockMvc.perform(post("/transactions").contentType("application/json").content(body))
                    .andReturn().getResponse().getStatus();
        };

        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(postTransaction);
            var second = executor.submit(postTransaction);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(new int[]{first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)})
                    .containsExactlyInAnyOrder(201, 409);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transactions WHERE external_id = ?", Integer.class, externalId)).isEqualTo(1);
    }

    @Test
    void rejectsInvalidTypeSpecificMetadataWithoutWritingRows() throws Exception {
        String externalId = UUID.randomUUID().toString();
        mockMvc.perform(post("/transactions").contentType("application/json")
                        .content(payload(externalId, "flight", "{}")))
                .andExpect(status().isBadRequest());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transactions WHERE external_id = ?", Integer.class, externalId)).isZero();
    }

    @Test
    void acceptsOtherTransactionTypesAndRejectsUnknownFeeKind() throws Exception {
        String[] types = {"hotel", "rail", "navan_fee", "navan_fee"};
        String[] metadata = {
                """
                {"name":"Hotel","address":{"street":"Main St","city":"Berlin","country":"DE"},
                 "check_in":"2026-03-11","check_out":"2026-03-12","nights":1,"room_rate_per_night":100.00}
                """,
                """
                {"legs":[{"origin":"Berlin","destination":"Hamburg",
                          "departure":"2026-03-11T10:00:00Z","arrival":"2026-03-11T12:00:00Z"}]}
                """,
                "{\"fee_kind\":\"trip_fee\"}",
                "{\"fee_kind\":\"agent_call_fee\"}"
        };
        for (int index = 0; index < types.length; index++) {
            mockMvc.perform(post("/transactions").contentType("application/json")
                            .content(payload(UUID.randomUUID().toString(), types[index], metadata[index])))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.type").value(types[index]));
        }
        mockMvc.perform(post("/transactions").contentType("application/json")
                        .content(payload(UUID.randomUUID().toString(), "navan_fee", "{\"fee_kind\":\"other\"}")))
                .andExpect(status().isBadRequest());
    }

    private String payload(String externalId, String type, String metadata) {
        return """
                {"type":"%s","external_id":"%s","currency":"EUR",
                 "occurred_at":"2026-03-11T10:00:00Z","total":220.00,
                 "tax_lines":[{"name":"Airport tax","amount":15.00}],
                 "fee_lines":[{"name":"YQ","amount":25.00}],"metadata":%s}
                """.formatted(type, externalId, metadata);
    }
}
