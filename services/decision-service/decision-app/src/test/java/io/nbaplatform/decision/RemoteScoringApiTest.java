package io.nbaplatform.decision;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import io.nbaplatform.decision.state.SnapshotStore;
import io.nbaplatform.events.CampaignAction;
import io.nbaplatform.events.OfferingChanged;
import io.nbaplatform.events.PropensityModel;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Python-served models through the Open Inference Protocol: a working model is used and explained, a broken
 * one degrades to the default prior for its own action only, and the decision is not a fallback.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RemoteScoringApiTest {

    static final HttpServer MODEL_SERVER;

    static {
        try {
            MODEL_SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        MODEL_SERVER.createContext("/v2/models/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            boolean good = exchange.getRequestURI().getPath().startsWith("/v2/models/churn/");
            byte[] reply = (good ? "{\"outputs\":[{\"name\":\"p\",\"shape\":[1,2],\"datatype\":\"FP64\","
                    + "\"data\":[0.1,0.9]}]}" : "{\"error\":\"model not loaded\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(good ? 200 : 500, reply.length);
            exchange.getResponseBody().write(reply);
            exchange.close();
        });
        MODEL_SERVER.start();
    }

    @DynamicPropertySource
    static void modelServer(DynamicPropertyRegistry registry) {
        registry.add("nba.decision.plugins.remote-model.enabled", () -> "true");
        registry.add("nba.decision.plugins.remote-model.base-url",
                () -> "http://127.0.0.1:" + MODEL_SERVER.getAddress().getPort());
        registry.add("nba.decision.plugins.remote-model.timeout", () -> "300ms");
        registry.add("nba.decision.plugins.propensity-scoring.timeout", () -> "400ms");
    }

    @AfterAll
    static void stop() {
        MODEL_SERVER.stop(0);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    SnapshotStore snapshot;

    @MockitoBean
    KafkaTemplate<String, String> kafka;

    private static OfferingChanged remoteOffering(String id, String model) {
        return new OfferingChanged(id, "Offer " + id, null, "retention", "Active", List.of("web"), 10, null,
                new PropensityModel("remote", model, "2", 0, null, List.of("tenureMonths")), false, 1);
    }

    @BeforeEach
    void seed() {
        TestData.seed(snapshot);
        snapshot.apply("acme", remoteOffering("retain", "churn"));
        snapshot.apply("acme", remoteOffering("winback", "broken"));
        snapshot.apply("acme", TestData.campaign("retention", "segment == 'at-risk'",
                new CampaignAction("retain", 1.0), new CampaignAction("winback", 1.0)));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());
    }

    @Test
    void remoteModelsAreUsedAndABrokenOneFallsBackToThePriorOnly() throws Exception {
        String body = """
                {"relatedParty":[{"id":"party-7"}],"channel":{"id":"web"},
                 "characteristic":[{"name":"tenureMonths","value":3},{"name":"segment","value":"at-risk"}],
                 "maxItems":2}
                """;
        mvc.perform(post(RecommendationApiTest.BASE).param("explain", "true").header("X-Tenant-Id", "acme")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fallback").value(false))
                .andExpect(jsonPath("$.recommendationItem[0].productOffering.id").value("retain"))
                .andExpect(jsonPath("$.recommendationItem[0].propensity").value(0.9))
                .andExpect(jsonPath("$.recommendationItem[0].explanation.scorer").value("remote"))
                .andExpect(jsonPath("$.recommendationItem[0].explanation.modelName").value("churn"))
                .andExpect(jsonPath("$.recommendationItem[0].explanation.contributions[0].feature")
                        .value("tenureMonths"))
                .andExpect(jsonPath("$.recommendationItem[0].explanation.contributions[0].value").value(3.0))
                .andExpect(jsonPath("$.recommendationItem[0].explanation.contributions[0].weight").doesNotExist())
                .andExpect(jsonPath("$.recommendationItem[1].productOffering.id").value("winback"))
                .andExpect(jsonPath("$.recommendationItem[1].propensity").value(0.05))
                .andExpect(jsonPath("$.recommendationItem[1].reasonCode").value(hasItem("SCORING_FALLBACK")))
                .andExpect(jsonPath("$.recommendationItem[1].explanation.scorer").value("prior"))
                .andExpect(jsonPath("$.recommendationItem[1].explanation.scoringFallback").value("SCORING_ERROR"));
    }
}
