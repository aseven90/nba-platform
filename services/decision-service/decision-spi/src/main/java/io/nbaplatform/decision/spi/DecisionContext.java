package io.nbaplatform.decision.spi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

/** Mutable state of one decision as it moves through the pipeline. Not shared between threads. */
public final class DecisionContext {

    private final String decisionId;
    private final DecisionRequest request;
    private final DecisionSnapshot snapshot;
    private final LearningState learning;
    private final ContactHistory contactHistory;
    private final List<Candidate> candidates = new ArrayList<>();
    private final List<Suppression> suppressed = new ArrayList<>();
    private String currentStage = "";

    public DecisionContext(String decisionId, DecisionRequest request, DecisionSnapshot snapshot,
                           LearningState learning, ContactHistory contactHistory) {
        this.decisionId = decisionId;
        this.request = request;
        this.snapshot = snapshot;
        this.learning = learning;
        this.contactHistory = contactHistory;
    }

    public String decisionId() {
        return decisionId;
    }

    public DecisionRequest request() {
        return request;
    }

    public DecisionSnapshot snapshot() {
        return snapshot;
    }

    public LearningState learning() {
        return learning;
    }

    public ContactHistory contactHistory() {
        return contactHistory;
    }

    /** Live candidates, in current rank order once a selection stage has run. */
    public List<Candidate> candidates() {
        return candidates;
    }

    public List<Suppression> suppressed() {
        return Collections.unmodifiableList(suppressed);
    }

    public void add(Candidate candidate) {
        candidates.add(candidate);
    }

    /** Records a candidate that never entered the live list (for example, an unknown offering). */
    public void reject(Candidate candidate, String reasonCode) {
        suppressed.add(new Suppression(candidate, currentStage, reasonCode));
    }

    /** Removes candidates for which {@code reason} returns a non-null reason code and records why. */
    public void suppressIf(Function<Candidate, String> reason) {
        Iterator<Candidate> it = candidates.iterator();
        while (it.hasNext()) {
            Candidate candidate = it.next();
            String code = reason.apply(candidate);
            if (code != null) {
                it.remove();
                suppressed.add(new Suppression(candidate, currentStage, code));
            }
        }
    }

    public void enterStage(String stageName) {
        this.currentStage = stageName;
    }
}
