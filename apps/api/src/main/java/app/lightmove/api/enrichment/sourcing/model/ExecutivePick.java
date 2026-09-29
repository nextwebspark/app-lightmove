package app.lightmove.api.enrichment.sourcing.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/**
 * One person a run filed at a company: who, and the row they became — null when filing them was
 * refused as already mapped. {@code score} and {@code reason} are a retired rerank's, read back only on
 * runs recorded before it went, and null since.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExecutivePick(String name, Integer score, String reason, UUID candidateId) {

    public static ExecutivePick of(String name, UUID candidateId) {
        return new ExecutivePick(name, null, null, candidateId);
    }
}
