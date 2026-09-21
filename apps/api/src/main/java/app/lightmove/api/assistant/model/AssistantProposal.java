package app.lightmove.api.assistant.model;

import java.util.List;
import java.util.UUID;

/**
 * Companies the assistant is offering to file, as the event carries them and the accept reads them
 * back.
 *
 * <p><b>{@code projectId} is the mandate the proposing tool call was authorised against</b>, not the
 * thread's — a thread's is nullable context the model did not have to name, and the guard ran
 * against the argument. Storing it here is what lets the accept re-authorise against the same
 * mandate rather than against anything the accepting request claims.
 *
 * <p>The stage is deliberately absent. A proposal names <i>what</i>, and a person names <i>where</i>
 * — the mockup's accept bar offers all three stages against one card, so a proposal that already
 * chose would be answering a question nobody asked it.
 */
public record AssistantProposal(UUID projectId, String title, List<ProposedCompany> companies) {

    public AssistantProposal {
        companies = companies == null ? List.of() : List.copyOf(companies);
    }

    /** The rows a set of refs names, in the proposal's own order, ignoring a ref it does not hold. */
    public List<ProposedCompany> refs(List<String> refs) {
        return companies.stream().filter(company -> refs.contains(company.ref())).toList();
    }
}
