package io.nbaplatform.decision.spi;

import io.nbaplatform.events.CampaignChanged;
import io.nbaplatform.events.OfferingChanged;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** An action proposed by a campaign, carried through the pipeline and annotated by each stage. */
public final class Candidate {

    private final OfferingChanged offering;
    private final CampaignChanged campaign;
    private final double priorityLever;
    private final List<String> reasonCodes = new ArrayList<>();
    private double modelPropensity;
    private double propensity;
    private double priority;
    private double value = Double.NaN;
    private ScoringExplanation scoring;

    public Candidate(OfferingChanged offering, CampaignChanged campaign, double priorityLever) {
        this.offering = offering;
        this.campaign = campaign;
        this.priorityLever = priorityLever;
    }

    public OfferingChanged offering() {
        return offering;
    }

    public CampaignChanged campaign() {
        return campaign;
    }

    public double priorityLever() {
        return priorityLever;
    }

    /** Propensity estimated by the model, before any exploration adjustment. */
    public double modelPropensity() {
        return modelPropensity;
    }

    public void modelPropensity(double value) {
        this.modelPropensity = value;
        this.propensity = value;
    }

    /** Propensity used for arbitration. */
    public double propensity() {
        return propensity;
    }

    public void propensity(double value) {
        this.propensity = value;
    }

    public double priority() {
        return priority;
    }

    public void priority(double value) {
        this.priority = value;
    }

    /** Business value the arbitration stage used, or NaN before arbitration. */
    public double value() {
        return value;
    }

    public void value(double value) {
        this.value = value;
    }

    /** How the scoring stage arrived at {@link #modelPropensity()}, or null before scoring. */
    public ScoringExplanation scoring() {
        return scoring;
    }

    public void scoring(ScoringExplanation scoring) {
        this.scoring = scoring;
    }

    public void reason(String code) {
        reasonCodes.add(code);
    }

    public List<String> reasonCodes() {
        return Collections.unmodifiableList(reasonCodes);
    }
}
