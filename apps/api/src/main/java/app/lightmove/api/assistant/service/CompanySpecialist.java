package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.model.AssistantProposal;
import app.lightmove.api.assistant.model.AssistantTurn;
import app.lightmove.api.assistant.tool.AssistantToolContext;
import app.lightmove.api.assistant.tool.CompanySearchTools;
import app.lightmove.api.assistant.tool.MandateTools;
import app.lightmove.api.assistant.tool.NamedCompanyTools;
import app.lightmove.api.assistant.tool.ProposalTools;
import app.lightmove.api.assistant.tool.SectorTools;
import app.lightmove.api.assistant.tool.TurnRecorder;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/** The market: searches the company universe and the vendor by name, and proposes a card of companies. */
@Service
public class CompanySpecialist implements AssistantSpecialist {

    /** Kept from before specialists existed, so the cost and log lines of a company answer read as they did. */
    private static final String PROMPT_ID = "assistant-turn";

    private final MandateTools mandateTools;
    private final CompanySearchTools searchTools;
    private final NamedCompanyTools namedCompanyTools;
    private final SectorTools sectorTools;
    private final ProposalTools proposalTools;
    private final HiringSideResolver hiringSides;
    private final Resource systemPrompt;

    public CompanySpecialist(MandateTools mandateTools, CompanySearchTools searchTools,
                             NamedCompanyTools namedCompanyTools, SectorTools sectorTools,
                             ProposalTools proposalTools, HiringSideResolver hiringSides,
                             @Value("classpath:prompts/assistant-system.st") Resource systemPrompt) {
        this.mandateTools = mandateTools;
        this.searchTools = searchTools;
        this.namedCompanyTools = namedCompanyTools;
        this.sectorTools = sectorTools;
        this.proposalTools = proposalTools;
        this.hiringSides = hiringSides;
        this.systemPrompt = systemPrompt;
    }

    @Override
    public String toolName() {
        return "askCompanySpecialist";
    }

    @Override
    public String domain() {
        return "companies";
    }

    @Override
    public String description() {
        return """
                Finds and recommends companies for this position: searches the company universe, looks \
                companies up by name, suggests sectors, and puts a card of companies in front of the \
                consultant to file. Ask it for anything about which companies or sectors to target.""";
    }

    @Override
    public boolean availableTo(AssistantToolContext context) {
        return context.projectId() != null;
    }

    @Override
    public String promptId() {
        return PROMPT_ID;
    }

    @Override
    public Resource systemPrompt() {
        return systemPrompt;
    }

    @Override
    public Map<String, Object> systemParams(AssistantToolContext context) {
        return Map.of(
                "hiring", HiringContext.render(hiringSides.resolve(context.workspaceId(), context.projectId())),
                "brief", BriefContext.render(mandateTools.briefOf(context.workspaceId(), context.projectId())));
    }

    @Override
    public List<Object> tools() {
        return List.of(mandateTools, searchTools, namedCompanyTools, sectorTools, proposalTools);
    }

    /** Researched pages ride on the stored card, so an earlier company can be proposed again unbilled. */
    @Override
    public void prepare(List<AssistantTurn> history, TurnRecorder recorder) {
        for (AssistantTurn turn : history) {
            AssistantProposal card = turn.getProposal();
            if (card == null) {
                continue;
            }
            card.researched().forEach(recorder::remember);
            card.companies().stream()
                    .filter(company -> company.operates() != null && company.key() != null)
                    .forEach(company -> recorder.operates(company.key(), company.operates()));
        }
    }

    @Override
    public void afterAnswer(AssistantToolContext context) {
        proposalTools.proposeWhatWasFound(context);
    }
}
