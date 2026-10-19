package io.nbaplatform.decision.spi;

/**
 * A pluggable step of the decision pipeline. Implementations must be thread-safe and must not block on I/O
 * beyond the request deadline; they mutate the {@link DecisionContext} they are given.
 */
public interface DecisionStage {

    Stage stage();

    /** Order within the stage; lower runs first. */
    default int order() {
        return 0;
    }

    /** Short identifier recorded in the decision log. */
    String name();

    void apply(DecisionContext context);
}
