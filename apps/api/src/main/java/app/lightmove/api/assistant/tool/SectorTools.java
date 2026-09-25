package app.lightmove.api.assistant.tool;

import app.lightmove.api.common.industry.service.Industries;
import app.lightmove.api.strategy.service.IndustryAdjacency;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** Which sectors sit beside the one a search is looking at — the Strategy panel's adjacency list. */
@Component
@RequiredArgsConstructor
public class SectorTools {

    private final IndustryAdjacency adjacency;

    @Tool(description = """
            List the industries whose executives transfer well into the given industry, from the \
            firm's own adjacency list. Call it whenever the consultant is looking at one sector, and \
            offer the adjacent industries that suit this role as the next place to look. Only an \
            industry listed here may be offered as adjacent. An empty list means the industry is not \
            one the universe knows — check its spelling with describeMarket.""")
    public AdjacentIndustries adjacentIndustries(
            @ToolParam(description = "Industry, written plainly (\"banking\", \"oil and gas\")") String industry,
            ToolContext toolContext) {
        TurnRecorder recorder = AssistantToolContext.from(toolContext).recorder();
        String asked = industry == null || industry.isBlank() ? "" : Industries.nameOf(industry);
        int step = recorder.startStep("Finding sectors next to " + asked);
        List<String> adjacent = adjacency.neighboursOf(asked);
        recorder.finishStep(step, adjacent.isEmpty() ? "None listed" : adjacent.size() + " adjacent");
        return new AdjacentIndustries(asked, adjacent);
    }
}
