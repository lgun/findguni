package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

public class V3__Stage_transitions extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (!hasColumn(connection, "game_stages", "entry_mode")) {
            execute(connection, "ALTER TABLE game_stages ADD COLUMN entry_mode VARCHAR(16) NOT NULL DEFAULT 'QR'");
            execute(connection, "UPDATE game_stages SET entry_mode = CASE WHEN qr_enabled = FALSE THEN 'START' ELSE 'QR' END");
        }
        if (!hasColumn(connection, "game_stages", "next_stage_key")) {
            execute(connection, "ALTER TABLE game_stages ADD COLUMN next_stage_key VARCHAR(36) NULL");
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
