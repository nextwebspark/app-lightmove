package app.lightmove.api.workspace.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The tables a purge empties, read off the schema: every {@code app_lm_} table with {@code workspace_id} but {@link #RETAINED},
 * children first in foreign-key order since {@code RESTRICT} refs refuse a parent's delete. Everything runs inside the
 * {@code WorkspaceScope}: a count outside one reads zero under RLS and would certify a purge that never happened.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkspacePurgeTables {

    /**
     * Kept by id after the purge. The audit trail and the credit ledger are append-only; the rest are the billing
     * records a financial record outlives a customer by, which reference the workspace without a cascade; and the
     * certificate is what the purge leaves behind. None holds a name: the ledger's and audit trail's people are ids.
     */
    public static final Set<String> RETAINED = Set.of(
            "app_lm_audit_event",
            "app_lm_credit_entry", "app_lm_credit_grant", "app_lm_credit_hold", "app_lm_credit_balance",
            "app_lm_credit_threshold_crossing", "app_lm_usage_event",
            "app_lm_workspace_subscription", "app_lm_billing_customer", "app_lm_billing_notice",
            "app_lm_workspace_purge_certificate",
            // A session's, keyed on the user: its workspace_id is a pointer the purge leaves for the user's next
            // sign-in to fall through, and the retention sweep deletes it once it has expired.
            "app_lm_refresh_token");

    private static final int BATCH = 1000;
    private static final Pattern TABLE_NAME = Pattern.compile("app_lm_[a-z0-9_]+");

    private static final String WORKSPACE_TABLES = """
            SELECT c.relname FROM pg_class c
             WHERE c.relkind = 'r' AND c.relnamespace = 'public'::regnamespace AND c.relname LIKE 'app\\_lm\\_%'
               AND EXISTS (SELECT 1 FROM pg_attribute a
                            WHERE a.attrelid = c.oid AND a.attname = 'workspace_id' AND NOT a.attisdropped)
             ORDER BY c.relname""";

    private static final String REFERENCES = """
            SELECT DISTINCT child.relname AS child, parent.relname AS parent
              FROM pg_constraint fk
              JOIN pg_class child ON child.oid = fk.conrelid
              JOIN pg_class parent ON parent.oid = fk.confrelid
             WHERE fk.contype = 'f' AND child.relname LIKE 'app\\_lm\\_%' AND parent.relname LIKE 'app\\_lm\\_%'
               AND child.relnamespace = 'public'::regnamespace""";

    private final JdbcTemplate jdbc;

    /** Every table the purge empties, in the order it empties them. */
    public List<String> purgeOrder() {
        Set<String> purged = new TreeSet<>(jdbc.queryForList(WORKSPACE_TABLES, String.class));
        purged.removeAll(RETAINED);
        purged.remove("app_lm_workspace");
        purged.forEach(WorkspacePurgeTables::requireTableName);
        return childrenFirst(purged, referencedBy());
    }

    public Map<String, Long> rowsOf(UUID workspaceId, Iterable<String> tables) {
        Map<String, Long> counts = new TreeMap<>();
        for (String table : tables) {
            Long rows = jdbc.queryForObject("SELECT count(*) FROM " + requireTableName(table)
                    + " WHERE workspace_id = ?", Long.class, workspaceId);
            counts.put(table, rows == null ? 0 : rows);
        }
        return counts;
    }

    /** Answers how many rows went; zero once the table holds none of the workspace's. Run in its own transaction. */
    public int deleteBatch(String table, UUID workspaceId) {
        String name = requireTableName(table);
        return jdbc.update("DELETE FROM " + name + " WHERE ctid = ANY (ARRAY (SELECT ctid FROM " + name
                + " WHERE workspace_id = ? LIMIT " + BATCH + "))", workspaceId);
    }

    /** Each table to the tables holding a reference to it, directly or through a table carrying no workspace. */
    private Map<String, Set<String>> referencedBy() {
        Map<String, Set<String>> children = new HashMap<>();
        jdbc.query(REFERENCES, row -> {
            String child = row.getString("child");
            String parent = row.getString("parent");
            if (!child.equals(parent)) {
                children.computeIfAbsent(parent, ignored -> new HashSet<>()).add(child);
            }
        });
        return children;
    }

    /** Kahn's walk over the purged tables, a table emitted once every table that must go before it has. */
    private static List<String> childrenFirst(Set<String> purged, Map<String, Set<String>> children) {
        Map<String, Set<String>> mustFollow = new LinkedHashMap<>();
        for (String table : purged) {
            mustFollow.put(table, purgedDependentsOf(table, purged, children));
        }
        List<String> order = new ArrayList<>();
        Set<String> left = new TreeSet<>(purged);
        while (!left.isEmpty()) {
            List<String> ready = left.stream()
                    .filter(table -> mustFollow.get(table).stream().noneMatch(left::contains))
                    .toList();
            if (ready.isEmpty()) {
                // A cycle of references; its cascades carry it, so it goes in name order.
                log.warn("Purge order has a reference cycle among {}; emptied in name order", left);
                order.addAll(left);
                break;
            }
            order.addAll(ready);
            ready.forEach(left::remove);
        }
        return order;
    }

    /**
     * The purged tables holding a reference to {@code table}, walking past tables without a workspace (a position, a
     * sequence's steps) since a row of those is only ever deleted by the cascade of the purged table above it.
     */
    private static Set<String> purgedDependentsOf(String table, Set<String> purged, Map<String, Set<String>> children) {
        Set<String> dependents = new HashSet<>();
        Set<String> seen = new HashSet<>();
        Deque<String> next = new ArrayDeque<>(children.getOrDefault(table, Set.of()));
        while (!next.isEmpty()) {
            String child = next.pop();
            if (!seen.add(child) || child.equals(table)) {
                continue;
            }
            if (purged.contains(child)) {
                dependents.add(child);
            } else if (!RETAINED.contains(child)) {
                next.addAll(children.getOrDefault(child, Set.of()));
            }
        }
        return dependents;
    }

    private static String requireTableName(String table) {
        if (!TABLE_NAME.matcher(table).matches()) {
            throw new IllegalStateException("not a table the purge may name: " + table);
        }
        return table;
    }
}
