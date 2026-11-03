package app.lightmove.api.enrichment.sourcing.model;

import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.sourcing.constant.TitleCheck;

/** One person a company search found, which index found them and how their title is known. */
public record FoundPerson(BrightDataPerson person, String provider, EnrichmentVendor researchedBy, TitleCheck check) {

    public boolean hasTitle() {
        return check == TitleCheck.LIVE_TITLE || check == TitleCheck.INDEX_TITLE;
    }

    public String slug() {
        return person.linkedinId();
    }
}
