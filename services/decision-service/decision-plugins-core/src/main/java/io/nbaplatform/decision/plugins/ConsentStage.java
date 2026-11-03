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
        Map<String, Object> attributes = context.request().attributes();
        String reason;
        if (isTrue(attributes.get("optOut"))) {
            reason = "OPTED_OUT";
        } else {
            Object consent = attributes.get("consent." + context.request().channel());
            boolean denied = consent == null ? requireExplicitConsent : !isTrue(consent);
            reason = denied ? "NO_CHANNEL_CONSENT" : null;
        }
        if (reason != null) {
            context.suppressIf(c -> reason);
        }
    }

    private static boolean isTrue(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }
}
