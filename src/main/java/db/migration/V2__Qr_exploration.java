package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

public class V2__Qr_exploration extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        if (!hasColumn(connection, "escape_games", "flow_mode")) {
            execute(connection, "ALTER TABLE escape_games ADD COLUMN flow_mode VARCHAR(24) NOT NULL DEFAULT 'LINEAR'");
        }
        execute(connection, "ALTER TABLE escape_games ALTER COLUMN flow_mode SET DEFAULT 'QR_EXPLORATION'");

        if (!hasColumn(connection, "game_stages", "qr_enabled")) {
            execute(connection, "ALTER TABLE game_stages ADD COLUMN qr_enabled BIT NOT NULL DEFAULT TRUE");
        }
        if (!hasColumn(connection, "play_sessions", "discovered_stages_json")) {
            execute(connection, "ALTER TABLE play_sessions ADD COLUMN discovered_stages_json LONGTEXT NULL");
        }
        if (!hasColumn(connection, "play_sessions", "solved_stages_json")) {
            execute(connection, "ALTER TABLE play_sessions ADD COLUMN solved_stages_json LONGTEXT NULL");
        }
        if (!hasColumn(connection, "play_sessions", "active_stage_key")) {
            execute(connection, "ALTER TABLE play_sessions ADD COLUMN active_stage_key VARCHAR(36) NULL");
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
