package app.lightmove.api.enrichment.importrun.model;

import org.springframework.modulith.NamedInterface;

/**
 * What an import's enrichment can do for this workspace now: whether a provider answers for people and for companies,
 * the most links one file may carry, and what this month's fair use leaves (null when nothing bounds it).
 */
@NamedInterface(name = "api", propagate = false)
public record EnrichmentOffer(boolean profilesOffered, boolean companiesOffered, int autoEnrichLimit,
                              Long allowanceLeft) {

    public boolean any() {
        return profilesOffered || companiesOffered;
    }
}
