package io.nbaplatform.decision.spi;

import java.util.Map;

/** Supplies stored features for a party (online feature store). Called once per decision, under the deadline. */
public interface FeatureProvider {

    Map<String, Object> features(String tenantId, String partyId);
}
