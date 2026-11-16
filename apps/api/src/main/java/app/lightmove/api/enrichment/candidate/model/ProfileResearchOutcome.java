package app.lightmove.api.enrichment.candidate.model;

import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.core.error.model.ApiException;

/** How one profile's research came out: held already, bought now, absent, not offered, or refused before any read. */
public sealed interface ProfileResearchOutcome {

    record OnFile(EnrichedProfile research) implements ProfileResearchOutcome {}

    record Fetched(EnrichedProfile research) implements ProfileResearchOutcome {}

    record NotFound() implements ProfileResearchOutcome {}

    record Unavailable() implements ProfileResearchOutcome {}

    /** Suppressed or restricted: researching them again would itself be processing. */
    record Refused(ApiException reason) implements ProfileResearchOutcome {}
}
