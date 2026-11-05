package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.enrichment.candidate.model.BrightDataPeopleHits;
import app.lightmove.api.enrichment.sourcing.model.SearchWords;
import app.lightmove.api.enrichment.sourcing.model.SearchedEmployer;
import app.lightmove.api.enrichment.sourcing.model.TitleTier;
import java.util.List;

/**
 * The live people index: every title as held today, counted free and bought a credit a profile. The
 * costly source, so a search counts before it buys, buys in tiers, and confirms a title by a free count
 * narrowed to one person's name rather than buying their profile.
 */
public interface LiveProfiles {

    boolean isOffered();

    /** Stored beside every record it returns, in the people cache. */
    String provider();

    EnrichmentVendor researchedBy();

    /** Free: how many people at the employer hold a title the tier finds, living in {@code countryCodes}. */
    long count(SearchedEmployer employer, TitleTier tier, List<String> countryCodes);

    /** Free: how many people at any of the employers hold a title {@code words} finds, living in {@code countryCodes}. */
    long countAcross(List<SearchedEmployer> employers, SearchWords words, List<String> countryCodes);

    /** A credit for every profile returned, those living elsewhere included; the caller narrows to the countries. */
    BrightDataPeopleHits search(SearchedEmployer employer, TitleTier tier, List<String> countryCodes, int size);

    /** Free: whether the person of this name at the employer holds a title {@code words} finds today. */
    boolean holdsTitle(SearchedEmployer employer, String personName, SearchWords words);

    /** One credit: that person's profile, when the live index finds them by name and title. */
    BrightDataPeopleHits profileOf(SearchedEmployer employer, String personName, SearchWords words);
}
