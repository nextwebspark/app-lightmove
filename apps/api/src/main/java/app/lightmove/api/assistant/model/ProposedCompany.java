package app.lightmove.api.assistant.model;

/**
 * One company the assistant is offering to file.
 *
 * <p>Identified by {@code ref} rather than by an Apollo id, which is the difference between a model
 * that grows and one that has to be replaced: a company the universe does not carry has no such id,
 * and the whole point of the card is that it lists those beside the ones it does.
 * {@link #apolloAccountId} is therefore nullable and meaningful only for {@link ProposalOrigin#UNIVERSE}.
 *
 * <p>Four fields beyond the identity, for {@code MarketCompanySummary}'s reason: a card is read by a
 * person deciding yes or no, and the grid is where the other twenty-three live.
 */
public record ProposedCompany(String ref, ProposalOrigin origin, String apolloAccountId,
                              String companyName, String country, Integer employees) {
}
