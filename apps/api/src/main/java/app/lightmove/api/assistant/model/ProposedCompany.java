package app.lightmove.api.assistant.model;

/**
 * One company on the assistant's card. Every field is copied from the universe row or the company's
 * LinkedIn page, never from the model. A universe company carries its Apollo account id; one found on
 * LinkedIn carries its slug instead. {@code operates} names the global brand this company runs locally.
 */
public record ProposedCompany(String apolloAccountId, String linkedinSlug, String companyName,
                              String country, Integer employees, String logoUrl, String operates) {

    /** How the card and an accept name this company: its account id, else its LinkedIn slug. */
    public String key() {
        return apolloAccountId != null ? apolloAccountId : linkedinSlug;
    }
}
