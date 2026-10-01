package app.lightmove.api.enrichment.peoplesearch.dto;

/**
 * The stored people filter counted, free. The estimates are ContactOut's own guess at how many of the
 * matches it holds each channel for — never a promise, and never bought.
 */
public record PeopleCountResponse(boolean offered, long total, long estimatedPersonalEmails,
                                  long estimatedWorkEmails, long estimatedPhones) {

    public static PeopleCountResponse none(boolean offered) {
        return new PeopleCountResponse(offered, 0, 0, 0, 0);
    }
}
