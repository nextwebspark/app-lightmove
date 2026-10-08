package app.lightmove.api.billing.plan.service;

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

    public List<UUID> activeIds() {
        return jdbc.queryForList("SELECT id FROM app_lm_workspace WHERE status = 'ACTIVE' ORDER BY id", UUID.class);
    }
}
