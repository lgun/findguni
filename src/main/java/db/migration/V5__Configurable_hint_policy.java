package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

public class V5__Configurable_hint_policy extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (!hasColumn(connection, "escape_games", "unlimited_hints")) {
            execute(connection, "ALTER TABLE escape_games ADD COLUMN unlimited_hints BIT NOT NULL DEFAULT TRUE");
        }
        if (!hasColumn(connection, "escape_games", "hint_limit")) {
            execute(connection, "ALTER TABLE escape_games ADD COLUMN hint_limit INT NOT NULL DEFAULT 3");
        }
        if (!hasColumn(connection, "escape_games", "hint_cooldown_seconds")) {
            execute(connection, "ALTER TABLE escape_games ADD COLUMN hint_cooldown_seconds INT NOT NULL DEFAULT 0");
        }
        if (!hasColumn(connection, "play_sessions", "revealed_hints_json")) {
            execute(connection, "ALTER TABLE play_sessions ADD COLUMN revealed_hints_json LONGTEXT NULL");
        }
        if (!hasColumn(connection, "play_sessions", "last_hint_at")) {
            execute(connection, "ALTER TABLE play_sessions ADD COLUMN last_hint_at DATETIME(6) NULL");
        }
    }

    private boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        return hasColumn(metadata, table, column)
                || hasColumn(metadata, table.toUpperCase(Locale.ROOT), column.toUpperCase(Locale.ROOT))
                || hasColumn(metadata, table.toLowerCase(Locale.ROOT), column.toLowerCase(Locale.ROOT));
    }

    private boolean hasColumn(DatabaseMetaData metadata, String table, String column) throws SQLException {
        try (ResultSet columns = metadata.getColumns(null, null, table, column)) {
            return columns.next();
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
