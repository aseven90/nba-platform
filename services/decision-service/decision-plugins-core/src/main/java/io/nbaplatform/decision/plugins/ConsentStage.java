package io.nbaplatform.decision.plugins;

import io.nbaplatform.decision.spi.DecisionContext;
import io.nbaplatform.decision.spi.DecisionStage;
import io.nbaplatform.decision.spi.Stage;
import java.util.Map;

/**
 * Privacy by design: suppresses everything when the party opted out ({@code optOut=true}) or has withdrawn
 * consent for the channel ({@code consent.<channel>=false}). Absent consent attributes mean no restriction;
 * deployments that require explicit opt-in set {@code requireExplicitConsent}.
 */
public class ConsentStage implements DecisionStage {

    private final boolean requireExplicitConsent;

    public ConsentStage(boolean requireExplicitConsent) {
        this.requireExplicitConsent = requireExplicitConsent;
    }

    @Override
    public Stage stage() {
        return Stage.ELIGIBILITY;
    }

    @Override
    public String name() {
        return "consent";
    }

    @Override
    public void apply(DecisionContext context) {
        String reason = denialReason(context.request().attributes(), context.request().channel(),
                requireExplicitConsent);
        if (reason != null) {
            context.suppressIf(c -> reason);
        }
    }

    /**
     * Returns {@code OPTED_OUT} or {@code NO_CHANNEL_CONSENT} when nothing may be shown to the party on the
     * channel, otherwise null. Also used by the safe default answer, which must respect consent too.
     */
    public static String denialReason(Map<String, Object> attributes, String channel, boolean requireExplicitConsent) {
        if (isTrue(attributes.get("optOut"))) {
            return "OPTED_OUT";
        }
        Object consent = attributes.get("consent." + channel);
        boolean denied = consent == null ? requireExplicitConsent : !isTrue(consent);
        return denied ? "NO_CHANNEL_CONSENT" : null;
    }

    private static boolean isTrue(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }
}
