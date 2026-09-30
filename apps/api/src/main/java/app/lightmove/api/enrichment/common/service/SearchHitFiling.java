package app.lightmove.api.enrichment.common.service;

import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;

/** What filing a people-search hit as a candidate needs from the hit, shared by Find executives and People search. */
public final class SearchHitFiling {

    private static final int MAX_NAME = 200;
    private static final List<String> NAME_INDEXES = List.of("app_lm_project_candidate_at_company_uk",
            "app_lm_project_candidate_unmapped_name_uk");

    private SearchHitFiling() {
    }

    /** Vendor text, so bounded to what the request accepts; the slug stands in for a hit with no name. */
    public static String nameOf(BrightDataPerson person) {
        String name = person.name() == null || person.name().isBlank() ? person.linkedinId() : person.name().strip();
        return name.length() <= MAX_NAME ? name : name.substring(0, MAX_NAME);
    }

    /** V36's two name indexes: someone of that name was filed at the company between the check and the insert. */
    public static boolean isNameCollision(DataIntegrityViolationException raced) {
        String cause = String.valueOf(raced.getMostSpecificCause().getMessage());
        return NAME_INDEXES.stream().anyMatch(cause::contains);
    }
}
