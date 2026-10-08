package app.lightmove.api.billing.plan.service;

import app.lightmove.api.billing.plan.model.BillingManager;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** The workspaces billing needs to know of, read by SQL: billing never depends on {@code workspace}. */
@Component
@RequiredArgsConstructor
public class BillingWorkspaces {

    private final JdbcTemplate jdbc;

    public void requireExists(UUID workspaceId) {
        Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM app_lm_workspace WHERE id = ?)",
                Boolean.class, workspaceId);
        if (!Boolean.TRUE.equals(exists)) {
            throw ApiException.of(ErrorCode.WORKSPACE_NOT_FOUND);
        }
    }

    public String nameOf(UUID workspaceId) {
        return jdbc.queryForObject("SELECT name FROM app_lm_workspace WHERE id = ?", String.class, workspaceId);
    }

    public List<UUID> activeIds() {
        return jdbc.queryForList("SELECT id FROM app_lm_workspace WHERE status = 'ACTIVE' ORDER BY id", UUID.class);
    }

    public List<BillingManager> managersOf(UUID workspaceId) {
        return jdbc.query("""
                SELECT DISTINCT u.email, u.full_name
                FROM app_lm_workspace_member m
                JOIN app_lm_workspace_member_role mr ON mr.member_id = m.id
                JOIN app_lm_role_action ra ON ra.role_id = mr.role_id
                JOIN app_lm_action a ON a.id = ra.action_id
                JOIN app_lm_user u ON u.id = m.user_id
                WHERE m.workspace_id = ? AND m.status = 'ACTIVE' AND u.status = 'ACTIVE' AND a.name = 'BILLING_MANAGE'
                ORDER BY u.email""",
                (row, rowNum) -> new BillingManager(row.getString("email"), row.getString("full_name")), workspaceId);
    }
}
