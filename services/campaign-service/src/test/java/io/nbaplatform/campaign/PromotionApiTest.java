package io.nbaplatform.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
class PromotionApiTest {

    private static final String BASE = "/tmf-api/promotionManagement/v4/promotion";

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
    void createAndActivateCampaign() throws Exception {
        String body = """
                {"name":"Winback Q4","channel":[{"id":"web"}],
                 "eligibilityRule":"tenureMonths > 6 and segment == 'gold'",
                 "controlGroupPercent":5,"maxImpressionsPerDay":3,
                 "action":[{"actionObjectId":"offer-1","priorityLever":1.2},{"actionObjectId":"offer-2"}]}
                """;
        String created = mvc.perform(post(BASE).header("X-Tenant-Id", "acme")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lifecycleStatus").value("draft"))
                .andExpect(jsonPath("$.action[1].priorityLever").value(1.0))
                .andExpect(jsonPath("$.action[0].actionType").value("recommendOffering"))
                .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(created).get("id").asText();

        mvc.perform(patch(BASE + "/" + id).header("X-Tenant-Id", "acme")
                        .contentType("application/merge-patch+json").content("{\"lifecycleStatus\":\"active\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycleStatus").value("active"))
                .andExpect(jsonPath("$.eligibilityRule").value("tenureMonths > 6 and segment == 'gold'"));

        mvc.perform(get(BASE + "/" + id).header("X-Tenant-Id", "acme")).andExpect(status().isOk());

        var events = outbox.findAll();
        assertThat(events).hasSize(2);
        JsonNode data = mapper.readTree(events.get(1).getPayload()).get("data");
        assertThat(data.get("lifecycleStatus").asText()).isEqualTo("active");
        assertThat(data.get("controlGroupPercent").asDouble()).isEqualTo(5.0);
        assertThat(data.at("/actions/0/offeringId").asText()).isEqualTo("offer-1");
    }

    @Test
    void rejectsInvalidRuleAndControlGroup() throws Exception {
        mvc.perform(post(BASE).header("X-Tenant-Id", "acme").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"eligibilityRule\":\"age >\",\"action\":[{\"actionObjectId\":\"o\"}]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(BASE).header("X-Tenant-Id", "acme").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"controlGroupPercent\":80,\"action\":[{\"actionObjectId\":\"o\"}]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(BASE).header("X-Tenant-Id", "acme").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"action\":[]}"))
                .andExpect(status().isBadRequest());
    }
}
