package app.lightmove.api.assistant.model;

/** What the person filed from a card: the stage they chose and how many rows landed. */
public record ProposalOutcome(String status, int added, int skipped) {
}
