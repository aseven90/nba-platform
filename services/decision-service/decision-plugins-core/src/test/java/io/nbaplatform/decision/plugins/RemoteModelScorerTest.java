package io.nbaplatform.decision.plugins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.nbaplatform.decision.spi.FeatureContribution;
import io.nbaplatform.decision.spi.Score;
import io.nbaplatform.decision.spi.ScoringFailedException;
import io.nbaplatform.events.PropensityModel;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Talks to a fake Open Inference Protocol server. */
class RemoteModelScorerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer server;
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<JsonNode> body = new AtomicReference<>();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile int status = 200;
    private volatile String reply = "{\"outputs\":[{\"name\":\"predict_proba\",\"shape\":[1,2],\"datatype\":\"FP64\","
            + "\"data\":[0.25,0.75]}]}";
    private volatile long delayMs = 0;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            path.set(exchange.getRequestURI().getRawPath());
            body.set(JSON.readTree(exchange.getRequestBody()));
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private RemoteModelScorer scorer(int minimumCalls) {
        return new RemoteModelScorer(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
                Duration.ofMillis(300), HttpClient.newHttpClient(), JSON,
                CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                        .minimumNumberOfCalls(minimumCalls)
                        .slidingWindowSize(minimumCalls)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofMinutes(1))
                        .build()));
    }

    private static PropensityModel model(String version) {
        return new PropensityModel("remote", "churn retention", version, 0, null,
                List.of("tenureMonths", "isGold", "missing"));
    }

    @Test
    void sendsFeaturesInModelOrderAndReadsThePositiveClassProbability() throws Exception {
        Score score = scorer(10).score(model("3"), Fixtures.request(Map.of("tenureMonths", 24, "isGold", true)));

        assertThat(path.get()).isEqualTo("/v2/models/churn%20retention/versions/3/infer");
        JsonNode input = body.get().path("inputs").path(0);
        assertThat(input.path("name").asText()).isEqualTo("input-0");
        assertThat(input.path("datatype").asText()).isEqualTo("FP64");
        assertThat(JSON.convertValue(input.path("shape"), int[].class)).containsExactly(1, 3);
        assertThat(JSON.convertValue(input.path("data"), double[].class)).containsExactly(24.0, 1.0, 0.0);
        assertThat(score.propensity()).isEqualTo(0.75);
        assertThat(score.contributions()).extracting(FeatureContribution::feature)
                .containsExactly("tenureMonths", "isGold", "missing");
        assertThat(score.contributions().get(0).weight()).isNull();
    }

    @Test
    void singleOutputIsThePropensityAndVersionIsOptional() throws Exception {
        reply = "{\"outputs\":[{\"name\":\"p\",\"shape\":[1,1],\"datatype\":\"FP64\",\"data\":[0.1]}]}";

        Score score = scorer(10).score(model(null), Fixtures.request(Map.of()));

        assertThat(path.get()).isEqualTo("/v2/models/churn%20retention/infer");
        assertThat(score.propensity()).isEqualTo(0.1);
    }

    @Test
    void serverErrorsAndMalformedOutputsFail() {
        status = 503;
        assertThatThrownBy(() -> scorer(10).score(model("1"), Fixtures.request(Map.of())))
                .isInstanceOf(IOException.class).hasMessageContaining("503");

        status = 200;
        reply = "{\"outputs\":[{\"data\":[0.1,0.2,0.7]}]}";
        assertThatThrownBy(() -> scorer(10).score(model("1"), Fixtures.request(Map.of())))
                .isInstanceOf(ScoringFailedException.class)
                .extracting(e -> ((ScoringFailedException) e).reasonCode()).isEqualTo("SCORING_INVALID_OUTPUT");
    }

    @Test
    void slowServerHitsTheHttpTimeout() {
        delayMs = 1_000;
        assertThatThrownBy(() -> scorer(10).score(model("1"), Fixtures.request(Map.of())))
                .isInstanceOf(HttpTimeoutException.class);
    }

    @Test
    void repeatedFailuresOpenTheCircuitAndStopCallingTheServer() {
        status = 500;
        RemoteModelScorer scorer = scorer(4);
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> scorer.score(model("1"), Fixtures.request(Map.of())))
                    .isInstanceOf(IOException.class);
        }
        assertThatThrownBy(() -> scorer.score(model("1"), Fixtures.request(Map.of())))
                .isInstanceOf(ScoringFailedException.class)
                .extracting(e -> ((ScoringFailedException) e).reasonCode()).isEqualTo("CIRCUIT_OPEN");
        assertThat(calls).hasValue(4);
    }

    @Test
    void remoteFailuresDegradeToThePriorInsideTheScoringStage() {
        status = 500;
        PropensityModel remote = model("1");
        Fixtures f = new Fixtures()
                .with(Fixtures.offering("o1", 10, remote))
                .with(Fixtures.offering("o2", 10, new PropensityModel("v1", 0, Map.of())))
                .with(Fixtures.campaign("c1", null, 0, 0, new io.nbaplatform.events.CampaignAction("o1", 1),
                        new io.nbaplatform.events.CampaignAction("o2", 1)));
        var ctx = f.context("p1", Map.of(), 3);
        PropensityScoringStage stage = new PropensityScoringStage(List.of(scorer(10), new LogisticScorer()), 0.05);

        Fixtures.run(ctx, new CampaignCandidateStage(), stage);
        stage.close();

        assertThat(ctx.candidates()).extracting(c -> c.offering().id() + "=" + c.modelPropensity())
                .containsExactly("o1=0.05", "o2=0.5");
        assertThat(ctx.candidates().get(0).reasonCodes()).containsExactly("SCORING_FALLBACK", "SCORING_ERROR");
    }
}
