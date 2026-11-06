package io.nbaplatform.decision.plugins;

import io.nbaplatform.decision.spi.Candidate;
import io.nbaplatform.decision.spi.DecisionContext;
import io.nbaplatform.decision.spi.DecisionRequest;
import io.nbaplatform.decision.spi.DecisionStage;
import io.nbaplatform.decision.spi.PropensityScorer;
import io.nbaplatform.decision.spi.Score;
import io.nbaplatform.decision.spi.ScoringExplanation;
import io.nbaplatform.decision.spi.ScoringFailedException;
import io.nbaplatform.decision.spi.Stage;
import io.nbaplatform.events.PropensityModel;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scores every candidate with its offering's propensity model, routed by model type to a
 * {@link PropensityScorer} plugin. Each distinct model is scored once per decision. In-process scorers run
 * inline; remote scorers run concurrently on virtual threads under a shared scoring timeout.
 *
 * <p>Scoring failures never break the decision. A model whose scorer is missing, throws, runs past the timeout
 * or returns a value outside [0, 1] is replaced by the default prior for the candidates that use it, with the
 * reason codes {@code SCORING_FALLBACK} and the specific cause, so rules, consent and contact policy still
 * apply. With {@link OnFailure#SAFE_DEFAULT} the stage instead fails the decision and the engine returns the
 * tenant's safe default answer.
 */
public class PropensityScoringStage implements DecisionStage, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PropensityScoringStage.class);

    public static final String SCORER_UNAVAILABLE = "SCORER_UNAVAILABLE";
    public static final String SCORING_ERROR = "SCORING_ERROR";
    public static final String SCORING_TIMEOUT = "SCORING_TIMEOUT";
    public static final String SCORING_INVALID_OUTPUT = "SCORING_INVALID_OUTPUT";

    /** What to do when a model cannot be scored. */
    public enum OnFailure {
        /** Use the default prior for the affected candidates and carry on (default). */
        PRIOR,
        /** Fail the decision so the engine returns the safe default answer. */
        SAFE_DEFAULT
    }

    private final Map<String, PropensityScorer> scorers;
    private final double defaultPropensity;
    private final Duration timeout;
    private final OnFailure onFailure;
    private final BiConsumer<String, String> fallbackListener;
    private final ExecutorService remoteCalls = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, Long> lastWarned = new ConcurrentHashMap<>();
    private static final long WARN_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(30);

    /**
     * @param fallbackListener told (scorer type, reason code) every time a model falls back, for metrics
     */
    public PropensityScoringStage(Collection<? extends PropensityScorer> scorers, double defaultPropensity,
                                  Duration timeout, OnFailure onFailure, BiConsumer<String, String> fallbackListener) {
        this.scorers = scorers.stream().collect(Collectors.toUnmodifiableMap(PropensityScorer::type,
                Function.identity()));
        if (!(defaultPropensity >= 0 && defaultPropensity <= 1)) {
            throw new IllegalArgumentException("default propensity must be in [0, 1]: " + defaultPropensity);
        }
        this.defaultPropensity = defaultPropensity;
        this.timeout = timeout;
        this.onFailure = onFailure;
        this.fallbackListener = fallbackListener == null ? (s, r) -> { } : fallbackListener;
    }

    public PropensityScoringStage(Collection<? extends PropensityScorer> scorers, double defaultPropensity) {
        this(scorers, defaultPropensity, Duration.ofMillis(40), OnFailure.PRIOR, null);
    }

    @Override
    public Stage stage() {
        return Stage.SCORING;
    }

    @Override
    public String name() {
        return "propensity-scoring";
    }

    @Override
    public void close() {
        remoteCalls.shutdownNow();
    }

    private record Result(Score score, String failure) {
    }

    @Override
    public void apply(DecisionContext context) {
        DecisionRequest request = context.request();
        Map<PropensityModel, Result> results = score(models(context.candidates()), request);

        if (onFailure == OnFailure.SAFE_DEFAULT) {
            results.entrySet().stream().filter(e -> e.getValue().failure() != null).findFirst().ifPresent(e -> {
                throw new ScoringFailedException(e.getValue().failure(),
                        "Model " + describe(e.getKey()) + " could not be scored: " + e.getValue().failure(), null);
            });
        }

        for (Candidate c : context.candidates()) {
            PropensityModel model = c.offering().propensityModel();
            if (model == null) {
                c.modelPropensity(defaultPropensity);
                c.scoring(new ScoringExplanation(ScoringExplanation.PRIOR, null, null, List.of(), null));
                c.reason("MODEL_DEFAULT_PRIOR");
                continue;
            }
            Result result = results.get(model);
            if (result.failure() == null) {
                c.modelPropensity(result.score().propensity());
                c.scoring(new ScoringExplanation(model.effectiveType(), model.name(), model.version(),
                        result.score().contributions(), null));
                c.reason("MODEL_" + (model.version() == null ? "UNVERSIONED" : model.version()));
            } else {
                c.modelPropensity(defaultPropensity);
                c.scoring(new ScoringExplanation(ScoringExplanation.PRIOR, model.name(), model.version(), List.of(),
                        result.failure()));
                c.reason("SCORING_FALLBACK");
                c.reason(result.failure());
            }
        }
    }

    private static Collection<PropensityModel> models(List<Candidate> candidates) {
        Map<PropensityModel, Boolean> distinct = new LinkedHashMap<>();
        for (Candidate c : candidates) {
            if (c.offering().propensityModel() != null) {
                distinct.put(c.offering().propensityModel(), Boolean.TRUE);
            }
        }
        return distinct.keySet();
    }

    private Map<PropensityModel, Result> score(Collection<PropensityModel> models, DecisionRequest request) {
        Map<PropensityModel, Result> results = new LinkedHashMap<>();
        Map<PropensityModel, Future<Score>> pending = new LinkedHashMap<>();
        long deadline = System.nanoTime() + timeout.toNanos();
        for (PropensityModel model : models) {
            PropensityScorer scorer = scorers.get(model.effectiveType());
            if (scorer == null) {
                results.put(model, failed(model, SCORER_UNAVAILABLE, null));
            } else if (scorer.remote()) {
                pending.put(model, remoteCalls.submit(() -> scorer.score(model, request)));
            } else {
                try {
                    results.put(model, validate(model, scorer.score(model, request)));
                } catch (Exception e) {
                    results.put(model, failed(model, reasonFor(e), e));
                }
            }
        }
        try {
            for (var entry : pending.entrySet()) {
                PropensityModel model = entry.getKey();
                try {
                    long remaining = Math.max(0, deadline - System.nanoTime());
                    results.put(model, validate(model, entry.getValue().get(remaining, TimeUnit.NANOSECONDS)));
                } catch (TimeoutException e) {
                    entry.getValue().cancel(true);
                    results.put(model, failed(model, SCORING_TIMEOUT, null));
                } catch (ExecutionException e) {
                    results.put(model, failed(model, reasonFor(e.getCause()), e.getCause()));
                }
            }
        } catch (InterruptedException e) {
            pending.values().forEach(f -> f.cancel(true));
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Decision interrupted while scoring", e);
        }
        return results;
    }

    private Result validate(PropensityModel model, Score score) {
        if (score == null || !(score.propensity() >= 0.0 && score.propensity() <= 1.0)) {
            return failed(model, SCORING_INVALID_OUTPUT, null);
        }
        return new Result(score, null);
    }

    private Result failed(PropensityModel model, String reason, Throwable cause) {
        fallbackListener.accept(model.effectiveType(), reason);
        // During an outage every decision falls back: warn once per model and cause per interval, count always.
        String key = describe(model) + "|" + reason;
        long now = System.nanoTime();
        Long last = lastWarned.get(key);
        if (last == null || now - last > WARN_INTERVAL_NANOS) {
            lastWarned.put(key, now);
            log.warn("Model {} fell back to the default prior ({}){}", describe(model), reason,
                    cause == null ? "" : ": " + cause);
        }
        return new Result(null, reason);
    }

    private static String reasonFor(Throwable e) {
        return e instanceof ScoringFailedException sfe && sfe.reasonCode() != null ? sfe.reasonCode() : SCORING_ERROR;
    }

    private static String describe(PropensityModel model) {
        return model.effectiveType() + ":" + (model.name() == null ? "-" : model.name()) + ":"
                + (model.version() == null ? "-" : model.version());
    }
}
