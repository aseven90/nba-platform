package io.nbaplatform.decision;

import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.nbaplatform.decision.spi.DecisionContext;
import io.nbaplatform.decision.spi.DecisionStage;
import io.nbaplatform.decision.spi.Stage;
import io.nbaplatform.decision.state.SnapshotStore;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** A broken or slow plugin must degrade to the safe default answer, never to an error. */
@SpringBootTest(properties = "nba.decision.deadline=100ms")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SafeDefaultTest {

    enum Mode { NORMAL, FAIL, SLOW }

    static volatile Mode mode = Mode.NORMAL;

    @TestConfiguration
    static class FaultyPlugin {
        @Bean
        DecisionStage faultyStage() {
            return new DecisionStage() {
                @Override
                public Stage stage() {
                    return Stage.SCORING;
                }

                @Override
                public int order() {
                    return 100;
                }

                @Override
                public String name() {
                    return "faulty";
                }

                @Override
                public void apply(DecisionContext context) {
                    if (mode == Mode.FAIL) {
                        throw new IllegalStateException("model server unavailable");
                    }
                    if (mode == Mode.SLOW) {
                        try {
                            Thread.sleep(1_000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            };
        }
    }

    private static final String BODY = """
            {"relatedParty":[{"id":"party-42"}],"channel":{"id":"web"},
             "characteristic":[{"name":"tenureMonths","value":24}]}
            """;

    @Autowired
    MockMvc mvc;

    @Autowired
    SnapshotStore snapshot;

    @MockitoBean
    KafkaTemplate<String, String> kafka;

    @BeforeEach
    void seed() {
        TestData.seed(snapshot);
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());
    }

    @AfterEach
    void reset() {
        mode = Mode.NORMAL;
    }

    @Test
    void pluginFailureReturnsSafeDefault() throws Exception {
        mode = Mode.FAIL;
        mvc.perform(post(RecommendationApiTest.BASE).header("X-Tenant-Id", "acme")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fallback").value(true))
                .andExpect(jsonPath("$.recommendationItem[0].productOffering.id").value("safe-1"))
                .andExpect(jsonPath("$.recommendationItem[0].reasonCode[0]").value("SAFE_DEFAULT"))
                .andExpect(jsonPath("$.recommendationItem[0].reasonCode[1]").value("PIPELINE_ERROR"));
    }

    @Test
    void safeDefaultSkipsRetiredAndOffChannelOfferingsButKeepsNotYetLoadedOnes() throws Exception {
        mode = Mode.FAIL;
        mvc.perform(post(RecommendationApiTest.BASE).param("explain", "true").header("X-Tenant-Id", "acme")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(jsonPath("$.fallback").value(true))
                .andExpect(jsonPath("$.fallbackReason").value("PIPELINE_ERROR"))
                .andExpect(jsonPath("$.recommendationItem[*].productOffering.id")
                        .value(contains("safe-1", "safe-not-yet-loaded")))
                .andExpect(jsonPath("$.recommendationItem[0].explanation").doesNotExist())
                .andExpect(jsonPath("$.suppressedItem[?(@.productOffering.id == 'safe-retired')].reasonCode")
                        .value("OFFERING_NOT_LIVE"))
                .andExpect(jsonPath("$.suppressedItem[?(@.productOffering.id == 'safe-sms')].reasonCode")
                        .value("OFFERING_CHANNEL_MISMATCH"))
                .andExpect(jsonPath("$.suppressedItem[0].stage").value("safe-default"));
    }

    @Test
    void safeDefaultRespectsOptOutAndChannelConsent() throws Exception {
        mode = Mode.FAIL;
        for (String characteristic : new String[] {"{\"name\":\"optOut\",\"value\":true}",
                "{\"name\":\"consent.web\",\"value\":false}"}) {
            String body = "{\"relatedParty\":[{\"id\":\"party-42\"}],\"channel\":{\"id\":\"web\"},"
                    + "\"characteristic\":[" + characteristic + "]}";
            mvc.perform(post(RecommendationApiTest.BASE).param("explain", "true").header("X-Tenant-Id", "acme")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.fallback").value(true))
                    .andExpect(jsonPath("$.recommendationItem").isEmpty())
                    .andExpect(jsonPath("$.suppressedItem[0].reasonCode").value(
                            characteristic.contains("optOut") ? "OPTED_OUT" : "NO_CHANNEL_CONSENT"));
        }
    }

    @Test
    void deadlineExceededReturnsSafeDefault() throws Exception {
        mode = Mode.SLOW;
        mvc.perform(post(RecommendationApiTest.BASE).header("X-Tenant-Id", "acme")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fallback").value(true))
                .andExpect(jsonPath("$.recommendationItem[0].reasonCode[1]").value("DEADLINE_EXCEEDED"));
    }

    @Test
    void healthyPipelineIsNotAFallback() throws Exception {
        mvc.perform(post(RecommendationApiTest.BASE).header("X-Tenant-Id", "acme")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(jsonPath("$.fallback").value(false));
    }
}
