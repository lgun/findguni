package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

public class V6__Item_combinations extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (!hasColumn(connection, "game_stages", "required_items")) {
            execute(connection, "ALTER TABLE game_stages ADD COLUMN required_items LONGTEXT NULL");
        }
        if (!hasColumn(connection, "game_stages", "consume_required_items")) {
            execute(connection, "ALTER TABLE game_stages ADD COLUMN consume_required_items BIT NOT NULL DEFAULT FALSE");
        }
        if (!hasColumn(connection, "play_sessions", "consumed_items_json")) {
            execute(connection, "ALTER TABLE play_sessions ADD COLUMN consumed_items_json LONGTEXT NULL");
        }
        execute(connection, "UPDATE game_stages SET required_items = required_item "
                + "WHERE required_items IS NULL AND required_item IS NOT NULL");
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
