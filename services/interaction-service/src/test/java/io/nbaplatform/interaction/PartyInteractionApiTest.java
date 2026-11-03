package io.nbaplatform.interaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.nbaplatform.platform.outbox.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PartyInteractionApiTest {

    private static final String BASE = "/tmf-api/partyInteractionManagement/v4/partyInteraction";
    private static final String BODY = """
            {"relatedParty":[{"id":"party-42","role":"customer"}],"channel":[{"id":"web"}],
             "decisionId":"d-1","productOffering":{"id":"offer-1"},"promotion":{"id":"camp-1"},
             "outcome":"ACCEPTED"}
            """;

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    OutboxRepository outbox;

    @BeforeEach
    void clean() {
        outbox.deleteAll();
    }

    @Test
    void recordsInteractionOnceForSameIdempotencyKey() throws Exception {
        String first = mvc.perform(post(BASE).header("X-Tenant-Id", "acme").header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$['@baseType']").value("PartyInteraction"))
                .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(first).get("id").asText();

        mvc.perform(post(BASE).header("X-Tenant-Id", "acme").header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        mvc.perform(get(BASE).header("X-Tenant-Id", "acme").param("relatedParty.id", "party-42"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"));

        var events = outbox.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getMessageKey()).isEqualTo("acme:party-42");
        assertThat(mapper.readTree(events.get(0).getPayload()).at("/data/outcome").asText()).isEqualTo("ACCEPTED");
    }

    @Test
    void rejectsInteractionWithoutParty() throws Exception {
        mvc.perform(post(BASE).header("X-Tenant-Id", "acme").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":[{\"id\":\"web\"}],\"productOffering\":{\"id\":\"o\"},\"outcome\":\"IMPRESSION\"}"))
                .andExpect(status().isBadRequest());
    }
}
