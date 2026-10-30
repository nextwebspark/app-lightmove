package app.lightmove.api.pairing.model;

import app.lightmove.api.candidate.dto.AddedByResponse;
import java.util.Map;
import java.util.UUID;

/**
 * What a sort reads that the line itself does not carry: each status key's place in the workspace's pipeline, each
 * company's live note count and who filed each executive — the last two a staff read's alone, empty otherwise.
 */
public record StageLineFacts(Map<String, Integer> statusRanks, Map<UUID, Integer> noteCounts,
                             Map<UUID, AddedByResponse> executiveAdders) {

    public StageLineFacts {
        statusRanks = Map.copyOf(statusRanks);
        noteCounts = Map.copyOf(noteCounts);
        executiveAdders = Map.copyOf(executiveAdders);
    }
}
