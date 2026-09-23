package app.lightmove.api.assistant.model;

import java.util.List;

/** The company card an answer carried. It names no stage: the person picks one when they file it. */
public record AssistantProposal(String title, List<ProposedCompany> companies) {

    public AssistantProposal {
        companies = companies == null ? List.of() : List.copyOf(companies);
    }
}
