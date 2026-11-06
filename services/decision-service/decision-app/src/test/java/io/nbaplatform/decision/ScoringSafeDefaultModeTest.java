package io.nbaplatform.decision;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.nbaplatform.decision.state.SnapshotStore;
import io.nbaplatform.events.CampaignAction;
import io.nbaplatform.events.OfferingChanged;
import io.nbaplatform.events.PropensityModel;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** With {@code on-failure: safe-default}, any model that cannot be scored turns the decision into the safe default. */
@SpringBootTest(properties = "nba.decision.plugins.propensity-scoring.on-failure=safe-default")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScoringSafeDefaultModeTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    SnapshotStore snapshot;

    @MockitoBean
    KafkaTemplate<String, String> kafka;

    @BeforeEach
    void seed() {
        TestData.seed(snapshot);
        // No remote scorer is configured, so this model cannot be scored.
        snapshot.apply("acme", new OfferingChanged("retain", "Retain", null, null, "Active", List.of("web"), 10, null,
                new PropensityModel("remote", "churn", "1", 0, null, List.of()), false, 1));
        snapshot.apply("acme", TestData.campaign("retention", "segment == 'at-risk'",
                new CampaignAction("retain", 1.0)));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());
    }

    @Test
    void unscorableModelReturnsTheSafeDefault() throws Exception {
        String body = """
                {"relatedParty":[{"id":"party-7"}],"channel":{"id":"web"},
                 "characteristic":[{"name":"segment","value":"at-risk"}]}
                """;
        mvc.perform(post(RecommendationApiTest.BASE).header("X-Tenant-Id", "acme")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fallback").value(true))
                .andExpect(jsonPath("$.fallbackReason").value("SCORING_FAILED"))
                .andExpect(jsonPath("$.recommendationItem[0].productOffering.id").value("safe-1"))
                .andExpect(jsonPath("$.recommendationItem[0].reasonCode[1]").value("SCORING_FAILED"));
    }
}
