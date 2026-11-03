package app.lightmove.api.enrichment.sourcing.model;

import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.sourcing.constant.FoundBy;
import app.lightmove.api.enrichment.sourcing.constant.TitleCheck;

/** One person a company search found: the record read, which index read it, how their title is known, and who found them. */
public record FoundPerson(BrightDataPerson person, String provider, EnrichmentVendor researchedBy, TitleCheck check,
                          FoundBy foundBy) {

    public boolean hasTitle() {
        return check == TitleCheck.LIVE_TITLE || check == TitleCheck.INDEX_TITLE;
    }

    public String slug() {
        return person.linkedinId();
    }

    public FoundPerson alsoFoundBy(FoundBy other) {
        return other == foundBy ? this : new FoundPerson(person, provider, researchedBy, check, FoundBy.BOTH);
    }

    /** The same person as the live index holds them, bought after a free check confirmed their title. */
    public FoundPerson asLive(BrightDataPerson live, String liveProvider, EnrichmentVendor liveResearchedBy) {
        return new FoundPerson(live, liveProvider, liveResearchedBy, TitleCheck.LIVE_TITLE, FoundBy.BOTH);
    }
}
