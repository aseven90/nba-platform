package io.nbaplatform.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.closeTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.nbaplatform.decision.state.SnapshotStore;
import io.nbaplatform.events.Topics;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecommendationApiTest {

    static final String BASE = "/tmf-api/recommendationManagement/v4/recommendation";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    SnapshotStore snapshot;

    @MockitoBean
    KafkaTemplate<String, String> kafka;

    @BeforeEach
    void seed() {
        TestData.seed(snapshot);
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());
    }

    @Test
    void ranksEligibleActionsAndLogsTheDecision() throws Exception {
        String body = """
                {"relatedParty":[{"id":"party-42","role":"customer"}],"channel":{"id":"web"},
                 "characteristic":[{"name":"tenureMonths","value":24},{"name":"segment","value":"gold"}],
                 "maxItems":2}
                """;
        mvc.perform(post(BASE).header("X-Tenant-Id", "acme").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$['@baseType']").value("Recommendation"))
                .andExpect(jsonPath("$.fallback").value(false))
                // upgrade: sigmoid(-3 + 1.2) * 100 = 14.2; addon: sigmoid(-1 + 1.2) * 20 = 11.0
                .andExpect(jsonPath("$.recommendationItem[0].productOffering.id").value("upgrade"))
                .andExpect(jsonPath("$.recommendationItem[0].priority").value(1))
                .andExpect(jsonPath("$.recommendationItem[0].promotion.id").value("growth"))
                .andExpect(jsonPath("$.recommendationItem[1].productOffering.id").value("addon"))
                .andExpect(jsonPath("$.recommendationItem[0].reasonCode[0]").value("MODEL_v1"))
                .andExpect(jsonPath("$.suppressedItem").doesNotExist())
                .andExpect(jsonPath("$.fallbackReason").doesNotExist())
                .andExpect(jsonPath("$.recommendationItem[0].explanation").doesNotExist());

        ArgumentCaptor<String> event = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq(Topics.DECISION_MADE), eq("acme:party-42"), event.capture());
        JsonNode logged = mapper.readTree(event.getValue());
        assertThat(logged.get("type").asText()).isEqualTo("io.nbaplatform.decision.made.v1");
        assertThat(logged.at("/data/items")).hasSize(2);
        assertThat(logged.at("/data/suppressed/0/campaignId").asText()).isEqualTo("vip-only");
        assertThat(logged.at("/data/suppressed/0/reasonCode").asText()).isEqualTo("RULE_NOT_MET");
        assertThat(logged.at("/data/strategy").asText()).contains("eligibility-rules").doesNotContain("thompson");
        JsonNode why = logged.at("/data/items/0/explanation");
        assertThat(why.get("scorer").asText()).isEqualTo("logistic");
        assertThat(why.get("modelVersion").asText()).isEqualTo("v1");
        assertThat(why.get("value").asDouble()).isEqualTo(100.0);
        assertThat(why.get("priority").asDouble()).isEqualTo(logged.at("/data/items/0/priority").asDouble());
        assertThat(why.at("/contributions/0/feature").asText()).isEqualTo("tenureMonths");
        assertThat(why.at("/contributions/0/contribution").asDouble()).isCloseTo(1.2, within(1e-9));
        // Privacy by design: the log explains the decision without copying the party's attribute values.
        assertThat(why.at("/contributions/0").has("value")).isFalse();
    }

    @Test
    void explainsSuppressedCandidatesOnRequest() throws Exception {
        String body = """
                {"relatedParty":[{"id":"party-42"}],"channel":{"id":"web"},
                 "characteristic":[{"name":"tenureMonths","value":24}],"maxItems":1}
                """;
        mvc.perform(post(BASE).param("explain", "true").header("X-Tenant-Id", "acme")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recommendationItem.length()").value(1))
                .andExpect(jsonPath("$.suppressedItem[?(@.reasonCode == 'RULE_NOT_EVALUABLE')].promotion.id")
                        .value("vip-only"))
                .andExpect(jsonPath("$.suppressedItem[?(@.reasonCode == 'RULE_NOT_EVALUABLE')].stage")
                        .value("eligibility-rules"))
                .andExpect(jsonPath("$.suppressedItem[?(@.reasonCode == 'RANKED_OUT')].productOffering.id")
                        .value("addon"))
                .andExpect(jsonPath("$.recommendationItem[0].explanation.scorer").value("logistic"))
                .andExpect(jsonPath("$.recommendationItem[0].explanation.modelVersion").value("v1"))
                .andExpect(jsonPath("$.recommendationItem[0].explanation.modelPropensity",
                        closeTo(1 / (1 + Math.exp(1.8)), 1e-9), Double.class))
                .andExpect(jsonPath("$.recommendationItem[0].explanation.value").value(100.0))
                .andExpect(jsonPath("$.recommendationItem[0].explanation.priorityLever").value(1.0))
                .andExpect(jsonPath("$.recommendationItem[0].explanation.contributions[0].feature")
                        .value("tenureMonths"))
                .andExpect(jsonPath("$.recommendationItem[0].explanation.contributions[0].value").value(24.0))
                .andExpect(jsonPath("$.recommendationItem[0].explanation.contributions[0].weight").value(0.05))
                .andExpect(jsonPath("$.recommendationItem[0].explanation.scoringFallback").doesNotExist());
    }

    @Test
    void returnsNoItemsWhenNothingIsEligible() throws Exception {
        String body = """
                {"relatedParty":[{"id":"party-1"}],"channel":{"id":"web"},
                 "characteristic":[{"name":"tenureMonths","value":2}]}
                """;
        mvc.perform(post(BASE).header("X-Tenant-Id", "acme").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fallback").value(false))
                .andExpect(jsonPath("$.recommendationItem").isEmpty());
    }

    @Test
    void validatesRequests() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(BASE).header("X-Tenant-Id", "acme").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":{\"id\":\"web\"}}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(BASE).header("X-Tenant-Id", "acme").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"relatedParty\":[{\"id\":\"p\"}],\"channel\":{\"id\":\"web\"},\"maxItems\":500}"))
                .andExpect(status().isBadRequest());
    }
}
