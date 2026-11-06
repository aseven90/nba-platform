package io.nbaplatform.decision.spi;

import io.nbaplatform.events.PropensityModel;

/**
 * Evaluates one kind of propensity model ({@link PropensityModel#type()}). Scorers are plugins: any jar can
 * contribute one as a bean, and the scoring stage routes each offering's model to the scorer of that type.
 *
 * <p>The scoring stage calls {@link #score} at most once per distinct model per decision, guards it with a
 * timeout, and replaces a failed, slow or invalid result with the default prior for the affected candidates
 * only. Implementations may therefore throw; they must be thread-safe.
 */
public interface PropensityScorer {

    /** The model type this scorer evaluates, for example {@code logistic} or {@code remote}. */
    String type();

    /**
     * True when scoring leaves the process (a model server). Remote scorers are called concurrently on virtual
     * threads under the scoring timeout; in-process scorers run inline.
     */
    default boolean remote() {
        return false;
    }

    /**
     * Scores {@code model} for the party and context in {@code request}.
     *
     * @return a propensity in [0, 1] and, where the model can say, the feature contributions behind it
     */
    Score score(PropensityModel model, DecisionRequest request) throws Exception;
}
