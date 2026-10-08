import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

public class UserDao {

    public static final long SESSION_MILLIS = 7L * 24 * 60 * 60 * 1000;   // 7 days
    private static final String DUMMY_SALT = "AAAAAAAAAAAAAAAAAAAAAA==";

    public static int count() throws SQLException {
        try (Connection c = Database.connect(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM users")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    // Creates an account. Returns the new id, or -1 if the username is already taken.
    // The very first account also takes over any data created before logins existed.
    public static int create(String username, String password) throws SQLException {
        boolean first = count() == 0;
        String salt = PasswordUtil.newSalt();
        String hash = PasswordUtil.hash(password, salt);

        try (Connection c = Database.connect()) {
            String insert = "INSERT INTO users (username, password_hash, salt, created_at) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = c.prepareStatement(insert)) {
                ps.setString(1, username);
                ps.setString(2, hash);
                ps.setString(3, salt);
                ps.setString(4, LocalDate.now().toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                    return -1;
                }
                throw e;
            }

            int id = -1;
            try (PreparedStatement ps = c.prepareStatement("SELECT id FROM users WHERE username = ?")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) id = rs.getInt(1);
                }
            }

            if (first && id > 0) {
                for (String table : new String[]{"prep_items", "company_placements"}) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE " + table + " SET user_id = ? WHERE user_id IS NULL")) {
                        ps.setInt(1, id);
                        ps.executeUpdate();
                    }
                }
            }
            return id;
        }
    }

    // Returns the user if the password is right, otherwise null
    public static User authenticate(String username, String password) throws SQLException {
        String sql = "SELECT id, password_hash, salt FROM users WHERE username = ?";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    PasswordUtil.hash(password, DUMMY_SALT);   // same work as a real login
                    return null;
                }
                if (PasswordUtil.matches(password, rs.getString("salt"), rs.getString("password_hash"))) {
                    return new User(rs.getInt("id"), username);
                }
                return null;
            }
        }
    }

    // Returns the raw token (goes in the cookie). Only its hash is stored.
    public static String createSession(int userId) throws SQLException {
        String token = PasswordUtil.newToken();
        String sql = "INSERT INTO sessions (token_hash, user_id, created_at) VALUES (?, ?, ?)";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, PasswordUtil.sha256Hex(token));
            ps.setInt(2, userId);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        }
        return token;
    }

    public static User findBySession(String token) throws SQLException {
        String sql = "SELECT u.id, u.username FROM sessions s JOIN users u ON u.id = s.user_id "
                   + "WHERE s.token_hash = ? AND s.created_at > ?";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, PasswordUtil.sha256Hex(token));
            ps.setLong(2, System.currentTimeMillis() - SESSION_MILLIS);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new User(rs.getInt(1), rs.getString(2)) : null;
            }
        }
    }

    public static void deleteSession(String token) throws SQLException {
        try (Connection c = Database.connect();
             PreparedStatement ps = c.prepareStatement("DELETE FROM sessions WHERE token_hash = ?")) {
            ps.setString(1, PasswordUtil.sha256Hex(token));
            ps.executeUpdate();
        }
    }
}
