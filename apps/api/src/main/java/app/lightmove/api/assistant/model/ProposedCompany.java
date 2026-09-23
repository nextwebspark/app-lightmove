package app.lightmove.api.assistant.model;

/** One company on the assistant's card. Every field is copied from the universe row, never from the model. */
public record ProposedCompany(String apolloAccountId, String companyName, String country,
                              Integer employees, String logoUrl) {
}
