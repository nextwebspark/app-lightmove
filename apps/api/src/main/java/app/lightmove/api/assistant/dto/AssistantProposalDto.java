package app.lightmove.api.assistant.dto;

import app.lightmove.api.assistant.model.AssistantProposal;
import app.lightmove.api.assistant.model.ProposedCompany;
import java.util.List;
import java.util.UUID;

/**
 * A proposal as the panel renders it, with what became of it.
 *
 * <p>{@code accepted} is non-null once somebody has filed it, and is what stops a refreshed page
 * offering a card that has already been actioned. The proposal itself is never rewritten — it is an
 * immutable event — so the outcome is a second one read alongside.
 */
public record AssistantProposalDto(UUID projectId, String title, List<ProposedCompany> companies,
                                   Accepted accepted) {

    /** What a person actually filed, and where. */
    public record Accepted(String status, List<String> refs, int added, int skipped) {}

    public static AssistantProposalDto of(AssistantProposal proposal, Accepted accepted) {
        return new AssistantProposalDto(proposal.projectId(), proposal.title(), proposal.companies(),
                accepted);
    }
}
