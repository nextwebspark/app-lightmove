package app.lightmove.api.mcp.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.mcp.model.McpRowsPage;
import app.lightmove.api.mcp.model.McpToolRefusal;
import app.lightmove.api.publicapi.dto.PublicPage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pages a public read for a tool: a cursor in, the rows from it and the next cursor out, held under the result cap. A
 * page the cap cuts short reads on from its first left-out row, so nothing is skipped and nothing sent twice.
 */
@Service
public class McpPaging {

    public static final int DEFAULT_LIMIT = 25;
    public static final int MAX_LIMIT = 100;

    /** Room left for everything around the rows: the page's own fields and the notice. */
    private static final int ENVELOPE_CHARS = 1000;

    private final Supplier<JsonMapper> json;
    private final int maxResultChars;

    /**
     * Measures with the mapper the transport writes with, as the guard does, so a page cut to fit is never then
     * refused whole. Looked up when first used: it exists only while the MCP server is on.
     */
    @Autowired
    public McpPaging(@Qualifier("mcpServerJsonMapper") ObjectProvider<JsonMapper> json,
                     LightMoveProperties properties) {
        this(json::getObject, properties.mcp().maxResultChars());
    }

    McpPaging(Supplier<JsonMapper> json, int maxResultChars) {
        this.json = json;
        this.maxResultChars = maxResultChars;
    }

    /** Reads {@code limit} rows from the cursor, through as many as two of the read's own pages. */
    public <T, R> McpRowsPage<R> page(String cursor, Integer limit,
                                       BiFunction<Integer, Integer, PublicPage<T>> read, Function<T, R> toRow) {
        int size = limitOf(limit);
        int offset = McpCursor.offsetOf(cursor);
        int pageNumber = offset / size;
        int skip = offset % size;
        PublicPage<T> first = read.apply(pageNumber, size);
        List<T> window = new ArrayList<>(first.data());
        if (skip > 0 && first.data().size() == size) {
            window.addAll(read.apply(pageNumber + 1, size).data());
        }
        List<R> rows = window.stream().skip(skip).limit(size).map(toRow).toList();
        int fitting = fittingCount(rows);
        int next = offset + fitting;
        String notice = fitting < rows.size()
                ? "Cut to " + fitting + " of " + rows.size() + " rows to stay within the size limit. Continue with "
                        + "nextCursor, narrow the query, or use response_format=concise."
                : null;
        return new McpRowsPage<>(rows.subList(0, fitting), first.totalCount(),
                next < first.totalCount() ? McpCursor.at(next) : null, notice);
    }

    /** How many of {@code rows}, from the first, fit the result cap beside {@code reserved} characters; never none. */
    public int fittingCount(List<?> rows, int reserved) {
        int budget = maxResultChars - ENVELOPE_CHARS - reserved;
        int used = 0;
        for (int index = 0; index < rows.size(); index++) {
            used += json.get().writeValueAsString(rows.get(index)).length() + 1;
            if (used > budget) {
                return Math.max(index, 1);
            }
        }
        return rows.size();
    }

    public boolean exceedsCap(Object value) {
        return charsOf(value) > maxResultChars;
    }

    public int charsOf(Object value) {
        return json.get().writeValueAsString(value).length();
    }

    private int fittingCount(List<?> rows) {
        return fittingCount(rows, 0);
    }

    private static int limitOf(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new McpToolRefusal("limit is between 1 and " + MAX_LIMIT + ".");
        }
        return limit;
    }
}
