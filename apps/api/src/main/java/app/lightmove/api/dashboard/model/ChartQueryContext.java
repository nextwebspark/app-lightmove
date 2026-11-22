package app.lightmove.api.dashboard.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Who asks and about what: the workspace always from the principal, the project only on a position's dashboard
 * (null on the workspace's), already checked to be the caller's to read. One context answers one request, and the
 * reads its charts share (the report, the executives, the positions) are made once and kept here for the rest.
 */
public final class ChartQueryContext {

    private final UUID userId;
    private final UUID workspaceId;
    private final UUID projectId;
    private final Map<String, Object> shared = new HashMap<>();
    private final Map<String, RuntimeException> failedReads = new HashMap<>();

    public ChartQueryContext(UUID userId, UUID workspaceId, UUID projectId) {
        this.userId = userId;
        this.workspaceId = workspaceId;
        this.projectId = projectId;
    }

    public UUID userId() {
        return userId;
    }

    public UUID workspaceId() {
        return workspaceId;
    }

    public UUID projectId() {
        return projectId;
    }

    /**
     * The read named {@code key}, made on first ask and kept for every later chart of this request. A read that fails
     * is kept failed too: each chart answers in its own transaction, so every later chart needing a slow shared read
     * would otherwise wait out the same time limit again.
     */
    @SuppressWarnings("unchecked")
    public <T> T sharedRead(String key, Supplier<T> read) {
        RuntimeException failed = failedReads.get(key);
        if (failed != null) {
            throw failed;
        }
        if (shared.containsKey(key)) {
            return (T) shared.get(key);
        }
        try {
            T value = read.get();
            shared.put(key, value);
            return value;
        } catch (RuntimeException failure) {
            failedReads.put(key, failure);
            throw failure;
        }
    }
}
