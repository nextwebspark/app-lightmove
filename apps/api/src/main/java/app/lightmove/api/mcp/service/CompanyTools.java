package app.lightmove.api.mcp.service;

import app.lightmove.api.core.security.apikey.ApiKeyScope;
import app.lightmove.api.core.security.apikey.PublicApiAuthorizer;
import app.lightmove.api.mcp.constant.McpResponseFormat;
import app.lightmove.api.mcp.dto.McpCompanyPage;
import app.lightmove.api.mcp.dto.McpCompanyRow;
import app.lightmove.api.mcp.model.McpRowsPage;
import app.lightmove.api.mcp.model.McpToolScopes;
import app.lightmove.api.publicapi.service.PublicReadService;
import io.modelcontextprotocol.common.McpTransportContext;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** The companies a position has filed, one stage at a time. */
@Component
@RequiredArgsConstructor
public class CompanyTools {

    static final String LIST = "uncava_list_companies";

    private final McpToolCalls calls;
    private final PublicReadService reads;
    private final PublicApiAuthorizer authorizer;
    private final McpPaging paging;

    @McpTool(name = LIST, generateOutputSchema = true, title = "List a position's companies",
            description = "List the companies a position has filed at one stage — inUniverse (the default), "
                    + "shortlisted or declined — newest first, a page at a time. Use it to see a market or a "
                    + "shortlist; to see the executives at those companies too, in one call, use "
                    + "uncava_get_universe. Example: stage \"shortlisted\" lists the shortlist.",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true,
                    openWorldHint = false))
    @McpToolScopes(ApiKeyScope.COMPANIES_READ)
    public McpCompanyPage listCompanies(
            McpTransportContext context,
            @McpToolParam(description = "The position's id, from uncava_search_positions") UUID positionId,
            @McpToolParam(description = "inUniverse (the default), shortlisted or declined", required = false)
            String stage,
            @McpToolParam(description = "The nextCursor of the previous page; omit for the first", required = false)
            String cursor,
            @McpToolParam(description = "Rows per page, 1 to 100; 25 when omitted", required = false) Integer limit,
            @McpToolParam(description = "concise (the default): names, stages, industry and country; detailed: "
                    + "every field", required = false) String response_format) {
        McpResponseFormat format = McpResponseFormat.parse(response_format);
        return calls.call(context, LIST, positionId, caller -> {
            authorizer.requireProjectRead(caller.reader(), positionId);
            McpRowsPage<McpCompanyRow> page = paging.page(cursor, limit,
                    (number, size) -> reads.companies(caller.reader(), positionId, stage, number, size),
                    company -> McpCompanyRow.of(company, format.isDetailed()));
            return new McpCompanyPage(page.rows(), page.totalCount(), page.nextCursor(), page.notice());
        }, page -> page.companies().size());
    }
}
