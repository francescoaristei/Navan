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
class TransactionListTests {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void followsIdCursorWithBoundedSummaryPages() throws Exception {
        long first = createTransaction();
        long second = createTransaction();
        long third = createTransaction();

        String firstPage = mockMvc.perform(get("/transactions")
                        .param("limit", "1").param("after_id", Long.toString(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(second))
                .andExpect(jsonPath("$.items[0].tax_lines").doesNotExist())
                .andExpect(jsonPath("$.items[0].metadata").doesNotExist())
                .andExpect(jsonPath("$.next_cursor").value(second))
                .andReturn().getResponse().getContentAsString();

        long cursor = objectMapper.readTree(firstPage).path("next_cursor").asLong();
        mockMvc.perform(get("/transactions")
                        .param("limit", "1").param("after_id", Long.toString(cursor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(third))
                .andExpect(jsonPath("$.next_cursor").value((Object) null));
    }

    @Test
    void defaultsToFiftyAndRejectsInvalidLimitsAndCursors() throws Exception {
        long anchor = createTransaction();
        long fiftieth = 0;
        long fiftyFirst = 0;
        for (int index = 1; index <= 51; index++) {
            long id = createTransaction();
            if (index == 50) fiftieth = id;
            if (index == 51) fiftyFirst = id;
        }

        mockMvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(50));

        String firstPage = mockMvc.perform(get("/transactions").param("after_id", Long.toString(anchor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(50))
                .andExpect(jsonPath("$.next_cursor").value(fiftieth))
                .andReturn().getResponse().getContentAsString();
        long cursor = objectMapper.readTree(firstPage).path("next_cursor").asLong();
        mockMvc.perform(get("/transactions").param("after_id", Long.toString(cursor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(fiftyFirst))
                .andExpect(jsonPath("$.next_cursor").value((Object) null));
        mockMvc.perform(get("/transactions").param("limit", "1000"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/transactions").param("limit", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/transactions").param("after_id", "-1"))
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
        long id = objectMapper.readTree(response).path("id").asLong();
        assertThat(id).isPositive();
        return id;
    }
}
