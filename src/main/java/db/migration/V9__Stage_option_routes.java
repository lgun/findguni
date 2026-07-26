package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

public class V9__Stage_option_routes extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (!hasColumn(connection, "game_stages", "option_routes_json")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE game_stages ADD COLUMN option_routes_json LONGTEXT");
            }
        }
    }

    private boolean hasColumn(Connection connection, String table, String column) throws Exception {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet columns = metadata.getColumns(null, null, table, column)) {
            if (columns.next()) return true;
        }
        try (ResultSet columns = metadata.getColumns(null, null,
                table.toUpperCase(), column.toUpperCase())) {
            return columns.next();
        }
    }
}
