package app.lightmove.api.core.tenant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Which tables hold a tenant's data, read off the schema rather than a list someone keeps: every {@code app_lm_}
 * table with a {@code workspace_id}, and every table holding a foreign key into one of those, however many hops
 * down. Each must have row-level security enabled, forced and given a policy, unless it is on {@link #EXCLUDED}
 * with its reason — so a migration cannot add an unprotected tenant table without someone writing down why.
 */
@Component
@RequiredArgsConstructor
public class TenantTableGuard {

    /**
     * Carry a workspace, yet are read before one is chosen or across all of a person's: the authorisation substrate
     * itself. Their children (a membership's roles) are out with them.
     */
    public static final Map<String, String> EXCLUDED = Map.of(
            "app_lm_workspace_member",
            "membership is what decides the workspace: sign-in, the switcher, consent and every authorizer read it "
                    + "across a person's workspaces",
            "app_lm_refresh_token",
            "a session's, keyed on the user; its workspace_id records which workspace the session is in");

    private static final String TENANT_TABLES = """
            WITH RECURSIVE tenant(oid) AS (
                SELECT c.oid FROM pg_class c
                 WHERE c.relkind = 'r' AND c.relnamespace = 'public'::regnamespace AND c.relname LIKE 'app\\_lm\\_%'
                   AND c.relname <> ALL (?::text[])
                   AND EXISTS (SELECT 1 FROM pg_attribute a
                                WHERE a.attrelid = c.oid AND a.attname = 'workspace_id' AND NOT a.attisdropped)
              UNION
                SELECT fk.conrelid FROM pg_constraint fk
                  JOIN tenant t ON fk.confrelid = t.oid
                  JOIN pg_class c ON c.oid = fk.conrelid
                 WHERE fk.contype = 'f' AND c.relname <> ALL (?::text[])
            )
            SELECT c.relname, pg_get_userbyid(c.relowner) = current_user AS owned
              FROM tenant t JOIN pg_class c ON c.oid = t.oid
             WHERE NOT (c.relrowsecurity AND c.relforcerowsecurity)
                OR NOT EXISTS (SELECT 1 FROM pg_policy p WHERE p.polrelid = c.oid)
             ORDER BY c.relname""";

    private static final String MISSING = """
            SELECT t.name FROM unnest(?::text[]) AS t(name)
             WHERE NOT EXISTS (SELECT 1 FROM pg_class c
                                WHERE c.relname = t.name AND c.relnamespace = 'public'::regnamespace)
             ORDER BY t.name""";

    /**
     * harden.sql gives these two to postgres, after which only it can protect them: a migration skips them where it
     * cannot, and they wait for harden.sql's next run ({@link #awaitingHarden}) rather than refusing the boot — the
     * deploy has migrated by then, and a revision refused would leave the one before it serving the new policies.
     */
    static final Set<String> HARDEN_OWNED = Set.of("app_lm_audit_event", "app_lm_credit_entry");

    private final JdbcTemplate jdbc;

    public List<String> faults() {
        String[] excluded = EXCLUDED.keySet().toArray(String[]::new);
        List<String> faults = new ArrayList<>();
        List<String> unprotected = unprotected().stream()
                .filter(table -> !table.awaitsHarden())
                .map(UnprotectedTable::name)
                .toList();
        if (!unprotected.isEmpty()) {
            faults.add("tenant tables without forced row-level security and a policy: " + String.join(", ", unprotected));
        }
        List<String> stale = jdbc.queryForList(MISSING, String.class, (Object) excluded);
        if (!stale.isEmpty()) {
            faults.add("excluded tables that no longer exist: " + String.join(", ", stale));
        }
        return faults;
    }

    /** {@link #HARDEN_OWNED} tables still unprotected, which only another run of harden.sql, as postgres, can fix. */
    public List<String> awaitingHarden() {
        return unprotected().stream().filter(UnprotectedTable::awaitsHarden).map(UnprotectedTable::name).toList();
    }

    private List<UnprotectedTable> unprotected() {
        String[] excluded = EXCLUDED.keySet().toArray(String[]::new);
        return jdbc.query(TENANT_TABLES, (row, index) -> new UnprotectedTable(row.getString("relname"),
                row.getBoolean("owned")), excluded, excluded);
    }

    private record UnprotectedTable(String name, boolean ownedByRuntimeRole) {

        boolean awaitsHarden() {
            return !ownedByRuntimeRole && HARDEN_OWNED.contains(name);
        }
    }
}
