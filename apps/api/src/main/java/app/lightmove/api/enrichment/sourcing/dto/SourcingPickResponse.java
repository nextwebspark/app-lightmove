package app.lightmove.api.enrichment.sourcing.dto;

import app.lightmove.api.enrichment.sourcing.model.ExecutivePick;
import java.util.UUID;

/**
 * One filed person as the strip lists it; {@code candidateId} is null when filing was refused, and
 * {@code score} and {@code reason} are set only on runs recorded while a rerank chose the picks.
 */
public record SourcingPickResponse(String name, Integer score, String reason, UUID candidateId) {

    static SourcingPickResponse of(ExecutivePick pick) {
        return new SourcingPickResponse(pick.name(), pick.score(), pick.reason(), pick.candidateId());
    }
}
