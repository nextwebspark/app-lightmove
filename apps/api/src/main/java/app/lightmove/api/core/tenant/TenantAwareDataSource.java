package app.lightmove.api.core.tenant;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Sets every borrowed connection's workspace from {@link TenantContext} for row-level security — none means the
 * protected tables answer nothing. At checkout, not transaction begin, since autocommit queries would miss a
 * transaction-local setting. Each checkout overwrites it (absent included), clears {@code app.rls_maintenance} and
 * sets {@code app.shared_rows} in one statement, so no flag outlives its borrower and only an open
 * {@link SharedRows} scope reaches a row of no workspace.
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
