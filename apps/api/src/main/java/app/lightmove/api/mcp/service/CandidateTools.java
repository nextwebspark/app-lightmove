package app.lightmove.api.mcp.service;

import app.lightmove.api.core.security.apikey.ApiKeyScope;
import app.lightmove.api.core.security.apikey.PublicApiAuthorizer;
import app.lightmove.api.mcp.constant.McpResponseFormat;
import app.lightmove.api.mcp.dto.McpCandidatePage;
import app.lightmove.api.mcp.dto.McpCandidateRow;
import app.lightmove.api.mcp.model.McpRowsPage;
import app.lightmove.api.mcp.model.McpToolScopes;
import app.lightmove.api.publicapi.service.PublicReadService;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** The executives a position has mapped. */
@Component
@RequiredArgsConstructor
public class CandidateTools {

    static final String LIST = "uncava_list_candidates";

    private final McpToolCalls calls;
    private final PublicReadService reads;
    private final PublicApiAuthorizer authorizer;
    private final McpPaging paging;

    @McpTool(name = LIST, generateOutputSchema = true, title = "List a position's executives",
            description = "List the executives a position has mapped, first mapped first, a page at a time, "
                    + "optionally only those at one status or at one of its companies. Detailed rows carry career, "
                    + "education and background; contacts and compensation only where this connection was granted "
                    + "them. Example: status \"interested\" lists who said yes.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    @McpToolScopes(ApiKeyScope.CANDIDATES_READ)
    public McpCandidatePage listCandidates(
            McpTransportContext context,
            @McpToolParam(description = "The position's id, from uncava_search_positions") UUID positionId,
            @McpToolParam(description = "Only executives at this status: identified, contacted, engaged, interested, "
                    + "notInterested, offLimits or outOfScope", required = false) String status,
            @McpToolParam(description = "Only executives mapped at this company: an id from uncava_list_companies",
                    required = false) UUID companyId,
            @McpToolParam(description = "The nextCursor of the previous page; omit for the first", required = false)
            String cursor,
            @McpToolParam(description = "Rows per page, 1 to 100; 25 when omitted", required = false) Integer limit,
            @McpToolParam(description = "concise (the default): names, titles, employers and status; detailed: "
                    + "every field", required = false) String response_format) {
        return calls.call(context, LIST, positionId, caller -> {
            McpResponseFormat format = McpResponseFormat.parse(response_format);
            authorizer.requireProjectRead(caller.reader(), positionId);
            McpRowsPage<McpCandidateRow> page = paging.page(cursor, limit,
                    (number, size) -> reads.candidates(caller.reader(), positionId, status, companyId, number, size),
                    candidate -> McpCandidateRow.of(candidate, format.isDetailed()));
            return new McpCandidatePage(page.rows(), page.totalCount(), page.nextCursor(), page.notice());
        }, page -> page.candidates().size());
    }
}
