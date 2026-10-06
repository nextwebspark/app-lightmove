package app.lightmove.api.mcp.model;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** One page of rows a tool sends: what fitted, how many there are in all, and where to read on. */
public record McpRowsPage<R>(List<R> rows, long totalCount, @Nullable String nextCursor, @Nullable String notice) {}
