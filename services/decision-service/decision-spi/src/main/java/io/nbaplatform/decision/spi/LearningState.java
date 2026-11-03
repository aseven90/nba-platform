package io.nbaplatform.decision.spi;

/** Observed response statistics per arm (campaign x offering), used by learning stages. */
public interface LearningState {

    ArmStats stats(String tenantId, String campaignId, String offeringId);

    record ArmStats(long impressions, long accepts) {
        public static final ArmStats EMPTY = new ArmStats(0, 0);

        public long failures() {
            return Math.max(0, impressions - accepts);
        }
    }
}
