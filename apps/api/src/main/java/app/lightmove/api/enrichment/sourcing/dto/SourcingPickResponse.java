package app.lightmove.api.enrichment.sourcing.dto;

import app.lightmove.api.enrichment.sourcing.model.ExecutivePick;
import java.util.UUID;

/** One pick as the strip lists it; {@code candidateId} is null when filing it was refused. */
public record SourcingPickResponse(String name, int score, String reason, UUID candidateId) {

    static SourcingPickResponse of(ExecutivePick pick) {
        return new SourcingPickResponse(pick.name(), pick.score(), pick.reason(), pick.candidateId());
    }
}
