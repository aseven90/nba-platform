package io.nbaplatform.decision.plugins;

import io.nbaplatform.decision.spi.Candidate;
import io.nbaplatform.decision.spi.DecisionContext;
import io.nbaplatform.decision.spi.DecisionStage;
import io.nbaplatform.decision.spi.Stage;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/** Ranks by priority, keeps the best campaign per offering and cuts the list to {@code maxItems}. */
public class TopNSelectionStage implements DecisionStage {

    @Override
    public Stage stage() {
        return Stage.SELECTION;
    }

    @Override
    public String name() {
        return "top-n";
    }

    @Override
    public void apply(DecisionContext context) {
        context.candidates().sort(Comparator.comparingDouble((Candidate c) -> c.priority()).reversed());
        Set<String> seen = new HashSet<>();
        context.suppressIf(c -> seen.add(c.offering().id()) ? null : "DUPLICATE_OFFERING");
        int max = context.request().maxItems();
        int[] rank = {0};
        context.suppressIf(c -> ++rank[0] > max ? "RANKED_OUT" : null);
    }
}
