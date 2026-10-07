package app.lightmove.api.billing.plan.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Whether a workspace a platform admin names exists, read by SQL: billing never depends on {@code workspace}. */
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
}
