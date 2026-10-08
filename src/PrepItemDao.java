import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Every query is limited to one user (user_id), so users can only ever see or change their own rows.
public class PrepItemDao {

    private static PrepItem map(ResultSet rs) throws SQLException {
        PrepItem p = new PrepItem();
        p.id = rs.getInt("id");
        p.title = rs.getString("title");
        p.category = rs.getString("category");
        p.difficulty = rs.getString("difficulty");
        p.status = rs.getString("status");
        p.targetDate = rs.getString("target_date");
        p.notes = rs.getString("notes");
        p.completedAt = rs.getString("completed_at");
        return p;
    }

    // Filters are optional: pass "" to ignore one. search looks in title and notes.
    public static List<PrepItem> findAll(int uid, String category, String status, String search) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM prep_items WHERE user_id = ?");
        List<String> params = new ArrayList<>();

        if (category != null && !category.isBlank()) {
            sql.append(" AND category = ?");
            params.add(category);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        if (search != null && !search.isBlank()) {
            sql.append(" AND (title LIKE ? OR notes LIKE ?)");
            params.add("%" + search + "%");
            params.add("%" + search + "%");
        }
        sql.append(" ORDER BY CASE status WHEN 'DONE' THEN 1 ELSE 0 END, target_date IS NULL, target_date");

        List<PrepItem> list = new ArrayList<>();
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

    public static PrepItem findById(int uid, int id) throws SQLException {
        String sql = "SELECT * FROM prep_items WHERE id = ? AND user_id = ?";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, uid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    // Next items that are not finished yet (used on the dashboard)
    public static List<PrepItem> findPending(int uid, int limit) throws SQLException {
        String sql = "SELECT * FROM prep_items WHERE user_id = ? AND status != 'DONE' "
                   + "ORDER BY target_date IS NULL, target_date LIMIT ?";
        List<PrepItem> list = new ArrayList<>();
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, uid);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    // Unfinished items whose target date is today or earlier
    public static List<PrepItem> findDue(int uid) throws SQLException {
        String sql = "SELECT * FROM prep_items WHERE user_id = ? AND status != 'DONE' "
                   + "AND target_date IS NOT NULL AND target_date <= ? ORDER BY target_date";
        List<PrepItem> list = new ArrayList<>();
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, uid);
            ps.setString(2, LocalDate.now().toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    // Unfinished topics for the auto-planner, easiest first
    public static List<PrepItem> findForPlanning(int uid, boolean onlyUnplanned) throws SQLException {
        String sql = "SELECT * FROM prep_items WHERE user_id = ? AND status != 'DONE'"
                   + (onlyUnplanned ? " AND (target_date IS NULL OR target_date = '')" : "")
                   + " ORDER BY CASE difficulty WHEN 'EASY' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END, id";
        List<PrepItem> list = new ArrayList<>();
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    // Saves the planner's dates (item i gets date i) in one transaction
    public static void setTargetDates(int uid, List<PrepItem> items, List<LocalDate> dates) throws SQLException {
        String sql = "UPDATE prep_items SET target_date = ? WHERE id = ? AND user_id = ?";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            c.setAutoCommit(false);
            for (int i = 0; i < items.size(); i++) {
                ps.setString(1, dates.get(i).toString());
                ps.setInt(2, items.get(i).id);
                ps.setInt(3, uid);
                ps.executeUpdate();
            }
            c.commit();
        }
    }

    // date -> number of topics marked DONE on that date, from fromDate onwards (GROUP BY)
    public static Map<String, Integer> completedPerDay(int uid, String fromDate) throws SQLException {
        String sql = "SELECT completed_at, COUNT(*) FROM prep_items "
                   + "WHERE user_id = ? AND completed_at IS NOT NULL AND completed_at >= ? "
                   + "GROUP BY completed_at";
        Map<String, Integer> map = new HashMap<>();
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, uid);
            ps.setString(2, fromDate);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString(1), rs.getInt(2));
                }
            }
        }
        return map;
    }

    // returns {total, done}
    public static int[] stats(int uid) throws SQLException {
        String sql = "SELECT COUNT(*), SUM(CASE WHEN status='DONE' THEN 1 ELSE 0 END) "
                   + "FROM prep_items WHERE user_id = ?";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new int[]{rs.getInt(1), rs.getInt(2)};
            }
        }
    }

    // Each row: {category, total, done}  (GROUP BY)
    public static List<String[]> categorySummary(int uid) throws SQLException {
        String sql = "SELECT category, COUNT(*), SUM(CASE WHEN status='DONE' THEN 1 ELSE 0 END) "
                   + "FROM prep_items WHERE user_id = ? GROUP BY category ORDER BY category";
        List<String[]> list = new ArrayList<>();
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new String[]{rs.getString(1),
                            String.valueOf(rs.getInt(2)), String.valueOf(rs.getInt(3))});
                }
            }
        }
        return list;
    }

    // Inserts every starter topic this user doesn't have yet. Returns how many were added.
    public static int seedStarter(int uid) throws SQLException {
        String sql = "INSERT INTO prep_items (title, category, difficulty, user_id) "
                   + "SELECT ?, ?, ?, ? WHERE NOT EXISTS "
                   + "(SELECT 1 FROM prep_items WHERE title = ? AND user_id = ?)";
        int added = 0;
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            c.setAutoCommit(false);
            for (String[] t : StarterTopics.LIST) {
                ps.setString(1, t[0]);
                ps.setString(2, t[1]);
                ps.setString(3, t[2]);
                ps.setInt(4, uid);
                ps.setString(5, t[0]);
                ps.setInt(6, uid);
                added += ps.executeUpdate();
            }
            c.commit();
        }
        return added;
    }

    public static void add(int uid, String title, String category, String difficulty,
                           String targetDate, String notes) throws SQLException {
        String sql = "INSERT INTO prep_items (title, category, difficulty, target_date, notes, user_id) "
                   + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, category);
            ps.setString(3, difficulty);
            ps.setString(4, (targetDate == null || targetDate.isBlank()) ? null : targetDate);
            ps.setString(5, notes);
            ps.setInt(6, uid);
            ps.executeUpdate();
        }
    }

    public static void update(int uid, int id, String title, String category, String difficulty,
                              String targetDate, String notes) throws SQLException {
        String sql = "UPDATE prep_items SET title = ?, category = ?, difficulty = ?, "
                   + "target_date = ?, notes = ? WHERE id = ? AND user_id = ?";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, category);
            ps.setString(3, difficulty);
            ps.setString(4, (targetDate == null || targetDate.isBlank()) ? null : targetDate);
            ps.setString(5, notes);
            ps.setInt(6, id);
            ps.setInt(7, uid);
            ps.executeUpdate();
        }
    }

    // Also records the day an item is finished (kept if it was already DONE, cleared if reopened)
    public static void updateStatus(int uid, int id, String status) throws SQLException {
        String sql = "UPDATE prep_items SET status = ?, "
                   + "completed_at = CASE WHEN ? = 'DONE' THEN COALESCE(completed_at, ?) ELSE NULL END "
                   + "WHERE id = ? AND user_id = ?";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, status);
            ps.setString(3, LocalDate.now().toString());
            ps.setInt(4, id);
            ps.setInt(5, uid);
            ps.executeUpdate();
        }
    }

    public static void delete(int uid, int id) throws SQLException {
        String sql = "DELETE FROM prep_items WHERE id = ? AND user_id = ?";
        try (Connection c = Database.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, uid);
            ps.executeUpdate();
        }
    }
}
