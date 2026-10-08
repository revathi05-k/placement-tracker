import java.sql.*;
import java.util.ArrayList;
import java.util.List;

// Every query is limited to one user (user_id), so users can only ever see or change their own rows.
public class CompanyDao {

    private static Company map(ResultSet rs) throws SQLException {
        Company c = new Company();
        c.id = rs.getInt("id");
        c.companyName = rs.getString("company_name");
        c.role = rs.getString("role");
        c.packageLpa = rs.getDouble("package_lpa");
        c.status = rs.getString("status");
        c.applicationDate = rs.getString("application_date");
        c.testDate = rs.getString("test_date");
        c.focusCategories = rs.getString("focus_categories");
        c.notes = rs.getString("notes");
        return c;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    // Filters are optional: pass "" to ignore one. search looks in company name and role.
    public static List<Company> findAll(int uid, String status, String search) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM company_placements WHERE user_id = ?");
        List<String> params = new ArrayList<>();

        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND (company_name LIKE ? OR role LIKE ?)");
            params.add("%" + search + "%");
            params.add("%" + search + "%");
        }
        sql.append(" ORDER BY id DESC");

        List<Company> list = new ArrayList<>();
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int i = 1;
            ps.setInt(i++, uid);
            for (String param : params) {
                ps.setString(i++, param);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    // Applications with a test date today or later that are still in play, soonest first
    public static List<Company> findUpcoming(int uid, String today) throws SQLException {
        String sql = "SELECT * FROM company_placements WHERE user_id = ? AND test_date IS NOT NULL "
                   + "AND test_date >= ? AND status NOT IN ('REJECTED', 'OFFER') ORDER BY test_date";
        List<Company> list = new ArrayList<>();
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, uid);
            ps.setString(2, today);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    public static Company findById(int uid, int id) throws SQLException {
        String sql = "SELECT * FROM company_placements WHERE id = ? AND user_id = ?";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, uid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    // returns {total applications, offers, interviews in progress}
    public static int[] stats(int uid) throws SQLException {
        String sql = "SELECT COUNT(*), "
                   + "SUM(CASE WHEN status='OFFER' THEN 1 ELSE 0 END), "
                   + "SUM(CASE WHEN status='INTERVIEW' THEN 1 ELSE 0 END) "
                   + "FROM company_placements WHERE user_id = ?";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new int[]{rs.getInt(1), rs.getInt(2), rs.getInt(3)};
            }
        }
    }

    // Each row: {status, count}  (GROUP BY)
    public static List<String[]> statusSummary(int uid) throws SQLException {
        String sql = "SELECT status, COUNT(*) FROM company_placements WHERE user_id = ? "
                   + "GROUP BY status ORDER BY status";
        List<String[]> list = new ArrayList<>();
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new String[]{rs.getString(1), String.valueOf(rs.getInt(2))});
                }
            }
        }
        return list;
    }

    public static void add(int uid, String companyName, String role, double packageLpa, String status,
                           String applicationDate, String testDate, String focusCategories,
                           String notes) throws SQLException {
        String sql = "INSERT INTO company_placements (company_name, role, package_lpa, status, "
                   + "application_date, test_date, focus_categories, notes, user_id) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, companyName);
            ps.setString(2, role);
            ps.setDouble(3, packageLpa);
            ps.setString(4, status);
            ps.setString(5, blankToNull(applicationDate));
            ps.setString(6, blankToNull(testDate));
            ps.setString(7, blankToNull(focusCategories));
            ps.setString(8, notes);
            ps.setInt(9, uid);
            ps.executeUpdate();
        }
    }

    public static void update(int uid, int id, String companyName, String role, double packageLpa,
                              String status, String applicationDate, String testDate,
                              String focusCategories, String notes) throws SQLException {
        String sql = "UPDATE company_placements SET company_name = ?, role = ?, package_lpa = ?, "
                   + "status = ?, application_date = ?, test_date = ?, focus_categories = ?, notes = ? "
                   + "WHERE id = ? AND user_id = ?";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, companyName);
            ps.setString(2, role);
            ps.setDouble(3, packageLpa);
            ps.setString(4, status);
            ps.setString(5, blankToNull(applicationDate));
            ps.setString(6, blankToNull(testDate));
            ps.setString(7, blankToNull(focusCategories));
            ps.setString(8, notes);
            ps.setInt(9, id);
            ps.setInt(10, uid);
            ps.executeUpdate();
        }
    }

    public static void updateStatus(int uid, int id, String status) throws SQLException {
        String sql = "UPDATE company_placements SET status = ? WHERE id = ? AND user_id = ?";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, id);
            ps.setInt(3, uid);
            ps.executeUpdate();
        }
    }

    public static void delete(int uid, int id) throws SQLException {
        String sql = "DELETE FROM company_placements WHERE id = ? AND user_id = ?";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, uid);
            ps.executeUpdate();
        }
    }
}
