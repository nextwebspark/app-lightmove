package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.PipelineColour;
import java.util.UUID;

/** Catalog rows for tests that build entities by hand, outside any workspace's seeded pipeline. */
public final class PipelineTestStatuses {

    private PipelineTestStatuses() {
    }

    /** A status under an active stage at {@code stagePosition}. */
    public static CandidateStatusDefinition active(String key, String label, int stagePosition) {
        CandidateStage stage = CandidateStage.added(UUID.randomUUID(), key + "Stage", label, PipelineColour.NEUTRAL,
                stagePosition, null);
        return CandidateStatusDefinition.added(stage, key, label, PipelineColour.NEUTRAL, 1, true, null);
    }

    public static CandidateStatusDefinition identified() {
        return active("identified", "Identified", 1);
    }
}
