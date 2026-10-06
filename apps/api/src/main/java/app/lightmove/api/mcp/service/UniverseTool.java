package app.lightmove.api.mcp.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.apikey.ApiKeyScope;
import app.lightmove.api.core.security.apikey.PublicApiAuthorizer;
import app.lightmove.api.core.security.apikey.PublicReader;
import app.lightmove.api.mcp.constant.McpResponseFormat;
import app.lightmove.api.mcp.dto.McpCandidateRow;
import app.lightmove.api.mcp.dto.McpCompanyRow;
import app.lightmove.api.mcp.dto.McpUniverse;
import app.lightmove.api.mcp.dto.McpUniverseCompany;
import app.lightmove.api.mcp.model.McpToolRefusal;
import app.lightmove.api.mcp.model.McpToolScopes;
import app.lightmove.api.publicapi.dto.PublicUniverse;
import app.lightmove.api.publicapi.service.PublicReadService;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** One stage of a position with its executives nested under their companies, in one call. */
@Component
@RequiredArgsConstructor
public class UniverseTool {

    static final String GET = "uncava_get_universe";

    private final McpToolCalls calls;
    private final PublicReadService reads;
    private final PublicApiAuthorizer authorizer;
    private final McpPaging paging;

    @McpTool(name = GET, generateOutputSchema = true, title = "Read a stage with its executives",
            description = "Read one stage of a position — inUniverse (the default), shortlisted or declined — as its "
                    + "companies, each with the executives mapped at it, in one call; on inUniverse, executives at no "
                    + "company come back as unassigned. Use it to summarise or compare a whole market at once; for a "
                    + "stage too large for one answer, page through uncava_list_companies and uncava_list_candidates "
                    + "instead. Example: stage \"shortlisted\" with response_format \"detailed\" for a shortlist "
                    + "briefing.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    @McpToolScopes({ApiKeyScope.COMPANIES_READ, ApiKeyScope.CANDIDATES_READ})
    public McpUniverse getUniverse(
            McpTransportContext context,
            @McpToolParam(description = "The position's id, from uncava_search_positions") UUID positionId,
            @McpToolParam(description = "inUniverse (the default), shortlisted or declined", required = false)
            String stage,
            @McpToolParam(description = "concise (the default) or detailed: every field of each company and executive",
                    required = false) String response_format) {
        McpResponseFormat format = McpResponseFormat.parse(response_format);
        return calls.call(context, GET, positionId, caller -> {
            authorizer.requireProjectRead(caller.reader(), positionId);
            return universeOf(readWhole(caller.reader(), positionId, stage), format.isDetailed());
        }, universe -> universe.companies().size() + universe.unassigned().size()
                + universe.companies().stream().mapToInt(company -> company.executives().size()).sum());
    }

    /** Refused past the export caps, as on the public API, but pointing at the tools rather than the routes. */
    private PublicUniverse readWhole(PublicReader reader, UUID positionId, String stage) {
        try {
            return reads.universe(reader, positionId, stage);
        } catch (ApiException refused) {
            if (refused.getCode() == ErrorCode.PUBLIC_API_UNIVERSE_TOO_LARGE) {
                throw new McpToolRefusal("This stage is too large to read in one call. Page through it with "
                        + "uncava_list_companies and uncava_list_candidates instead.");
            }
            throw refused;
        }
    }

    private McpUniverse universeOf(PublicUniverse universe, boolean detailed) {
        List<McpUniverseCompany> companies = universe.companies().stream()
                .map(company -> new McpUniverseCompany(McpCompanyRow.of(company.company(), detailed),
                        company.executives().stream().map(candidate -> McpCandidateRow.of(candidate, detailed))
                                .toList()))
                .toList();
        List<McpCandidateRow> unassigned = universe.unassigned().stream()
                .map(candidate -> McpCandidateRow.of(candidate, detailed)).toList();
        int fitting = paging.fittingCount(companies, paging.charsOf(unassigned));
        String notice = fitting < companies.size()
                ? "Cut to " + fitting + " of " + companies.size() + " companies to stay within the size limit. Read "
                        + "the stage a page at a time with uncava_list_companies and uncava_list_candidates, or use "
                        + "response_format=concise."
                : null;
        return new McpUniverse(universe.stage(), companies.subList(0, fitting), companies.size(), unassigned, notice);
    }
}
