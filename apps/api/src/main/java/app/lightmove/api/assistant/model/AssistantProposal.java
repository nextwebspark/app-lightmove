package app.lightmove.api.assistant.model;

import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import java.util.List;
import java.util.Map;

/**
 * The company card an answer carried. It names no stage: the person picks one when they file it.
 * {@code researched} holds, by LinkedIn slug, what the page said about each company the universe does
 * not carry — what filing one writes, so the row lands whole rather than as a name.
 */
public record AssistantProposal(String title, List<ProposedCompany> companies,
                                Map<String, CapturedCompanyDetails> researched) {

    public AssistantProposal {
        companies = companies == null ? List.of() : List.copyOf(companies);
        researched = researched == null ? Map.of() : Map.copyOf(researched);
    }
}
