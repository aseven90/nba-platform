package io.nbaplatform.decision.engine;

import io.nbaplatform.decision.spi.DecisionContext;
import io.nbaplatform.decision.spi.DecisionStage;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Runs every registered {@link DecisionStage}, ordered by stage then by order within the stage. */
@Component
public class DecisionPipeline {

    private static final Logger log = LoggerFactory.getLogger(DecisionPipeline.class);

    private final List<DecisionStage> stages;

    public DecisionPipeline(List<DecisionStage> stages) {
        this.stages = stages.stream()
                .sorted(Comparator.comparing(DecisionStage::stage).thenComparingInt(DecisionStage::order))
                .toList();
        log.info("Decision pipeline: {}", this.stages.stream().map(s -> s.stage() + ":" + s.name()).toList());
    }

    public void run(DecisionContext context) {
        for (DecisionStage stage : stages) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("Decision interrupted at stage " + stage.name());
            }
            context.enterStage(stage.name());
            stage.apply(context);
        }
    }

    public String describe() {
        return String.join(">", stages.stream().map(DecisionStage::name).toList());
    }
}
