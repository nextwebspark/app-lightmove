package app.lightmove.api.mcp.service;

import app.lightmove.api.core.security.apikey.ApiKeyScope;
import app.lightmove.api.core.security.apikey.PublicApiAuthorizer;
import app.lightmove.api.mcp.constant.McpResponseFormat;
import app.lightmove.api.mcp.dto.McpPositionPage;
import app.lightmove.api.mcp.dto.McpPositionRow;
import app.lightmove.api.mcp.model.McpRowsPage;
import app.lightmove.api.mcp.model.McpToolScopes;
import app.lightmove.api.publicapi.service.PublicReadService;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** The positions a connection can read: found by title, and read one at a time. */
@Component
@RequiredArgsConstructor
public class PositionTools {

    static final String SEARCH = "uncava_search_positions";
    static final String GET = "uncava_get_position";

    private final McpToolCalls calls;
    private final PublicReadService reads;
    private final PublicApiAuthorizer authorizer;
    private final McpPaging paging;

    @McpTool(name = SEARCH, generateOutputSchema = true, title = "Search positions",
            description = "List the positions (search and mapping mandates) this connection can read, newest first, "
                    + "optionally only those whose role title contains some text. Start here: every other tool takes "
                    + "a positionId from this list. Example: title \"CFO\" finds \"Group CFO – Energy\".",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    @McpToolScopes(ApiKeyScope.PROJECTS_READ)
    public McpPositionPage searchPositions(
            McpTransportContext context,
            @McpToolParam(description = "Only positions whose role title contains this, ignoring case",
                    required = false) String title,
            @McpToolParam(description = "The nextCursor of the previous page; omit for the first", required = false)
            String cursor,
            @McpToolParam(description = "Rows per page, 1 to 100; 25 when omitted", required = false) Integer limit,
            @McpToolParam(description = "concise (the default): ids, titles and stages; detailed: every field",
                    required = false) String response_format) {
        McpResponseFormat format = McpResponseFormat.parse(response_format);
        return calls.call(context, SEARCH, null, caller -> {
            McpRowsPage<McpPositionRow> page = paging.page(cursor, limit,
                    (number, size) -> reads.projects(caller.reader(), title, number, size),
                    project -> McpPositionRow.of(project, format.isDetailed()));
            return new McpPositionPage(page.rows(), page.totalCount(), page.nextCursor(), page.notice());
        }, page -> page.positions().size());
    }

    @McpTool(name = GET, generateOutputSchema = true, title = "Get a position",
            description = "Read one position by its id: role, client, type, stage, dates and how many companies and "
                    + "executives it holds. Use uncava_search_positions to find the id.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    @McpToolScopes(ApiKeyScope.PROJECTS_READ)
    public McpPositionRow getPosition(
            McpTransportContext context,
            @McpToolParam(description = "The position's id, from uncava_search_positions") UUID positionId,
            @McpToolParam(description = "concise (the default) or detailed: every field", required = false)
            String response_format) {
        McpResponseFormat format = McpResponseFormat.parse(response_format);
        return calls.call(context, GET, positionId, caller -> {
            authorizer.requireProjectRead(caller.reader(), positionId);
            return McpPositionRow.of(reads.project(caller.reader(), positionId), format.isDetailed());
        }, position -> 1);
    }
}
