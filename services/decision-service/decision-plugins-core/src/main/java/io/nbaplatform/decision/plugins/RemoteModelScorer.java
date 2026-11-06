package io.nbaplatform.decision.plugins;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.nbaplatform.decision.spi.DecisionRequest;
import io.nbaplatform.decision.spi.FeatureContribution;
import io.nbaplatform.decision.spi.PropensityScorer;
import io.nbaplatform.decision.spi.Score;
import io.nbaplatform.decision.spi.ScoringFailedException;
import io.nbaplatform.events.PropensityModel;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Scores {@code remote} models on an external model server speaking the Open Inference Protocol (KServe v2 REST,
 * also served by Seldon MLServer, NVIDIA Triton and others), so models trained in Python can be served without
 * re-implementing them in Java.
 *
 * <p>Request: {@code POST {baseUrl}/v2/models/{name}[/versions/{version}]/infer} with one FP64 input tensor
 * {@code input-0} of shape {@code [1, features]}, filled from the decision attributes in the model's
 * {@code features} order. Response: the first output's data is either {@code [p]} or class probabilities
 * {@code [p0, p1]} (scikit-learn {@code predict_proba}); the propensity is the last value.
 *
 * <p>Each call has its own HTTP timeout, and each model has a circuit breaker so a failing model server is not
 * called on every decision; an open breaker reports {@code CIRCUIT_OPEN} and the scoring stage falls back to
 * the default prior.
 */
public class RemoteModelScorer implements PropensityScorer {

    public static final String CIRCUIT_OPEN = "CIRCUIT_OPEN";

    private final URI baseUrl;
    private final Duration timeout;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final CircuitBreakerRegistry breakers;

    public RemoteModelScorer(URI baseUrl, Duration timeout, HttpClient http, ObjectMapper mapper,
                             CircuitBreakerRegistry breakers) {
        String base = baseUrl.toString();
        this.baseUrl = URI.create(base.endsWith("/") ? base.substring(0, base.length() - 1) : base);
        this.timeout = timeout;
        this.http = http;
        this.mapper = mapper;
        this.breakers = breakers;
    }

    @Override
    public String type() {
        return PropensityModel.REMOTE;
    }

    @Override
    public boolean remote() {
        return true;
    }

    @Override
    public Score score(PropensityModel model, DecisionRequest request) throws Exception {
        if (model.name() == null || model.name().isBlank()) {
            throw new ScoringFailedException(PropensityScoringStage.SCORING_ERROR, "remote model has no name", null);
        }
        CircuitBreaker breaker = breakers.circuitBreaker("model-" + model.name());
        try {
            return breaker.executeCallable(() -> call(model, request));
        } catch (CallNotPermittedException e) {
            throw new ScoringFailedException(CIRCUIT_OPEN, "circuit open for model " + model.name(), e);
        }
    }

    private Score call(PropensityModel model, DecisionRequest request) throws IOException, InterruptedException {
        List<String> features = model.features() == null ? List.of() : model.features();
        Map<String, Object> attributes = request.attributes();
        List<FeatureContribution> inputs = new ArrayList<>(features.size());
        ObjectNode body = mapper.createObjectNode();
        ObjectNode input = body.putArray("inputs").addObject();
        input.put("name", "input-0");
        input.putArray("shape").add(1).add(features.size());
        input.put("datatype", "FP64");
        ArrayNode data = input.putArray("data");
        for (String feature : features) {
            double value = Features.numeric(attributes.get(feature));
            data.add(value);
            inputs.add(FeatureContribution.input(feature, value));
        }

        HttpRequest httpRequest = HttpRequest.newBuilder(inferUri(model))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body)))
                .build();
        HttpResponse<byte[]> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("model server answered " + response.statusCode() + " for model " + model.name());
        }
        JsonNode out = mapper.readTree(response.body()).path("outputs").path(0).path("data");
        if (!out.isArray() || out.isEmpty() || out.size() > 2 || !out.get(out.size() - 1).isNumber()) {
            throw new ScoringFailedException(PropensityScoringStage.SCORING_INVALID_OUTPUT,
                    "unexpected output from model " + model.name() + ": " + out, null);
        }
        return new Score(out.get(out.size() - 1).asDouble(), inputs);
    }

    private URI inferUri(PropensityModel model) {
        StringBuilder path = new StringBuilder(baseUrl.toString()).append("/v2/models/").append(encode(model.name()));
        if (model.version() != null && !model.version().isBlank()) {
            path.append("/versions/").append(encode(model.version()));
        }
        return URI.create(path.append("/infer").toString());
    }

    private static String encode(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
