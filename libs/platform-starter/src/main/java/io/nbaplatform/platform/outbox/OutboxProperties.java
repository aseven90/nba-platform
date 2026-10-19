package io.nbaplatform.platform.outbox;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("nba.outbox")
public class OutboxProperties {

    /** Whether the relay publishes to Kafka. Disable to only write rows (e.g. in tests or when CDC is used). */
    private boolean relayEnabled = true;

    private int batchSize = 100;

    private Duration sendTimeout = Duration.ofSeconds(10);

    /** How long published rows are kept before cleanup. */
    private Duration retention = Duration.ofHours(24);

    public boolean isRelayEnabled() {
        return relayEnabled;
    }

    public void setRelayEnabled(boolean relayEnabled) {
        this.relayEnabled = relayEnabled;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public Duration getSendTimeout() {
        return sendTimeout;
    }

    public void setSendTimeout(Duration sendTimeout) {
        this.sendTimeout = sendTimeout;
    }

    public Duration getRetention() {
        return retention;
    }

    public void setRetention(Duration retention) {
        this.retention = retention;
    }
}
