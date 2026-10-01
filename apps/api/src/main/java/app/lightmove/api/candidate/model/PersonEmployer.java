package app.lightmove.api.candidate.model;

import java.util.UUID;

/**
 * The employer the Candidates page names beside a person, with where its logo may be found: the
 * mandate company row it was recorded against, else the logo research sent for that same employer.
 */
public record PersonEmployer(String name, UUID triageCompanyId, String researchedLogoUrl) {

    public static final PersonEmployer NONE = new PersonEmployer(null, null, null);
}
