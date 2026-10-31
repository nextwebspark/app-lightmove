package app.lightmove.api.core.tenant;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Hands every borrowed connection the workspace {@link TenantContext} names, so row-level security reads that
 * workspace's rows and no other's — or, with none, the protected tables' nothing.
 *
 * <p>At checkout rather than at transaction begin: Spring Data's declared query methods and a {@code JdbcTemplate}
 * outside a transaction run in autocommit, where a transaction-local setting would be gone before the statement
 * ran. Every checkout overwrites the setting, an absent workspace included, so a pooled connection never carries
 * one borrower's workspace into the next.
 */
class TenantAwareDataSource extends DelegatingDataSource {

    private static final String SET_WORKSPACE = "select set_config('app.workspace_id', ?, false)";

    TenantAwareDataSource(DataSource pool) {
        super(pool);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return inCurrentWorkspace(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return inCurrentWorkspace(super.getConnection(username, password));
    }

    private static Connection inCurrentWorkspace(Connection connection) throws SQLException {
        try {
            setWorkspace(connection, TenantContext.currentWorkspaceId());
        } catch (SQLException | RuntimeException failure) {
            connection.close();
            throw failure;
        }
        return connection;
    }

    static void setWorkspace(Connection connection, @Nullable UUID workspaceId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SET_WORKSPACE)) {
            statement.setString(1, workspaceId == null ? "" : workspaceId.toString());
            statement.execute();
        }
    }
}
