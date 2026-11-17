package app.lightmove.api.dataimport.dto;

import app.lightmove.api.enrichment.importrun.model.EnrichmentOffer;

/**
 * Whether the mapping step offers enrichment, the most links a file may carry for it, and what this month's fair use
 * leaves ({@code null} when nothing bounds it).
 */
public record ImportEnrichmentOfferDto(boolean profilesOffered, boolean companiesOffered, int autoEnrichLimit,
                                       Long allowanceLeft) {

    public static ImportEnrichmentOfferDto of(EnrichmentOffer offer) {
        return new ImportEnrichmentOfferDto(offer.profilesOffered(), offer.companiesOffered(),
                offer.autoEnrichLimit(), offer.allowanceLeft());
    }
}
