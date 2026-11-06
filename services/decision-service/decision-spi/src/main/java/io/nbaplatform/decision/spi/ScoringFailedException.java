package io.nbaplatform.decision.spi;

/**
 * Thrown by a scoring stage configured to fail the whole decision (and so return the safe default answer)
 * rather than fall back to the default prior per candidate.
 */
public class ScoringFailedException extends RuntimeException {

    private final String reasonCode;

    public ScoringFailedException(String reasonCode, String message, Throwable cause) {
        super(message, cause);
        this.reasonCode = reasonCode;
    }

    public String reasonCode() {
        return reasonCode;
    }
}
