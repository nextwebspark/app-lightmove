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
 * one borrower's workspace into the next — and clears {@code app.rls_maintenance} and sets {@code app.shared_rows} in
 * the same statement, so a flag set on a borrowed connection, by mistake or by injected SQL, dies with that borrower,
 * and only an open {@link SharedRows} scope reaches a row of no workspace. The cost is one round trip per checkout.
 */
class TenantAwareDataSource extends DelegatingDataSource {

    private static final String SET_WORKSPACE = "select set_config('app.workspace_id', ?, false)";
    private static final String SET_SHARED_ROWS = "select set_config('app.shared_rows', ?, false)";
    private static final String SET_ON_CHECKOUT = "select set_config('app.workspace_id', ?, false),"
            + " set_config('app.rls_maintenance', '', false), set_config('app.shared_rows', ?, false)";

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
            try (PreparedStatement statement = connection.prepareStatement(SET_ON_CHECKOUT)) {
                statement.setString(1, valueOf(TenantContext.currentWorkspaceId()));
                statement.setString(2, sharedRowsValue(SharedRows.isOpen()));
                statement.execute();
            }
        } catch (SQLException | RuntimeException failure) {
            connection.close();
            throw failure;
        }
        return connection;
    }

    static void setWorkspace(Connection connection, @Nullable UUID workspaceId) throws SQLException {
        set(connection, SET_WORKSPACE, valueOf(workspaceId));
    }

    static void setSharedRows(Connection connection, boolean open) throws SQLException {
        set(connection, SET_SHARED_ROWS, sharedRowsValue(open));
    }

    private static void set(Connection connection, String sql, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            statement.execute();
        }
    }

    private static String valueOf(@Nullable UUID workspaceId) {
        return workspaceId == null ? "" : workspaceId.toString();
    }

    private static String sharedRowsValue(boolean open) {
        return open ? "on" : "";
    }
}
