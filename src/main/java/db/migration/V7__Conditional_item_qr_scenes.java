package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

public class V7__Conditional_item_qr_scenes extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (!hasColumn(connection, "game_items", "alternate_required_item")) {
            execute(connection, "ALTER TABLE game_items ADD COLUMN alternate_required_item VARCHAR(36) NULL");
        }
        if (!hasColumn(connection, "game_items", "alternate_scan_text")) {
            execute(connection, "ALTER TABLE game_items ADD COLUMN alternate_scan_text LONGTEXT NULL");
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
