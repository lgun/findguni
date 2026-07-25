package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

public class V8__Server_heartbeats extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (!hasTable(connection, "server_heartbeats")) {
            execute(connection,
                    "CREATE TABLE server_heartbeats (" +
                    "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                    "server_name VARCHAR(120) NOT NULL, " +
                    "recorded_at DATETIME(6) NOT NULL, " +
                    "created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), " +
                    "INDEX idx_server_heartbeats_recorded_at (recorded_at)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private boolean hasTable(Connection connection, String table) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        return hasTable(metadata, table)
                || hasTable(metadata, table.toUpperCase(Locale.ROOT))
                || hasTable(metadata, table.toLowerCase(Locale.ROOT));
    }

    private boolean hasTable(DatabaseMetaData metadata, String table) throws SQLException {
        try (ResultSet tables = metadata.getTables(null, null, table, new String[]{"TABLE"})) {
            return tables.next();
        }
    }
}
