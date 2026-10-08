import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {

    // Where the database file lives. Set the DB_PATH environment variable to change it
    // (useful on a host with a persistent disk). Default: data/tracker.db
    private static final String DB_FILE = envOr("DB_PATH", "data/tracker.db");
    private static final String URL = "jdbc:sqlite:" + DB_FILE;

    private static String envOr(String name, String fallback) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    // Creates tables and upgrades databases made by older versions
    public static void init() {
        File parent = new File(DB_FILE).getAbsoluteFile().getParentFile();
        if (parent != null) parent.mkdirs();

        String users = "CREATE TABLE IF NOT EXISTS users ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "username TEXT NOT NULL UNIQUE, "
                + "password_hash TEXT NOT NULL, "
                + "salt TEXT NOT NULL, "
                + "created_at TEXT NOT NULL)";

        String sessions = "CREATE TABLE IF NOT EXISTS sessions ("
                + "token_hash TEXT PRIMARY KEY, "
                + "user_id INTEGER NOT NULL, "
                + "created_at INTEGER NOT NULL)";

        String prepItems = "CREATE TABLE IF NOT EXISTS prep_items ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "title TEXT NOT NULL, "
                + "category TEXT NOT NULL, "
                + "difficulty TEXT NOT NULL DEFAULT 'MEDIUM', "
                + "status TEXT NOT NULL DEFAULT 'TODO', "
                + "target_date TEXT, "
                + "notes TEXT)";

        String companies = "CREATE TABLE IF NOT EXISTS company_placements ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "company_name TEXT NOT NULL, "
                + "role TEXT NOT NULL, "
                + "package_lpa REAL, "
                + "status TEXT NOT NULL DEFAULT 'APPLIED', "
                + "application_date TEXT, "
                + "notes TEXT)";

        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute(users);
            stmt.execute(sessions);
            stmt.execute(prepItems);
            stmt.execute(companies);

            // Columns added in later versions (skipped automatically if they already exist)
            ensureColumn(conn, "prep_items", "completed_at", "TEXT");
            ensureColumn(conn, "company_placements", "test_date", "TEXT");
            ensureColumn(conn, "company_placements", "focus_categories", "TEXT");
            ensureColumn(conn, "prep_items", "user_id", "INTEGER");
            ensureColumn(conn, "company_placements", "user_id", "INTEGER");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_prep_user ON prep_items(user_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_company_user ON company_placements(user_id)");

            // Remove expired login sessions
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM sessions WHERE created_at < ?")) {
                ps.setLong(1, System.currentTimeMillis() - UserDao.SESSION_MILLIS);
                ps.executeUpdate();
            }

            System.out.println("Database ready (" + DB_FILE + ").");
        } catch (SQLException e) {
            System.out.println("Database init error: " + e.getMessage());
        }
    }

    // Adds a column only if the table doesn't have it yet (a tiny "migration")
    private static void ensureColumn(Connection conn, String table, String column, String type)
            throws SQLException {
        boolean exists = false;
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    exists = true;
                    break;
                }
            }
        }
        if (!exists) {
            try (Statement s = conn.createStatement()) {
                s.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
            }
        }
    }
}
