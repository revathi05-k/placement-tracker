import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Pure logic (no database, no HTML) so it can be tested on its own. See InsightsTest.java.
public class Insights {

    // ---------- Auto-planner ----------

    // Gives each topic a date: perDay topics per day, optionally skipping Sundays.
    public static List<LocalDate> assignDates(int count, LocalDate start, int perDay, boolean skipSundays) {
        List<LocalDate> dates = new ArrayList<>();
        if (perDay < 1) perDay = 1;
        LocalDate day = start;
        int usedToday = 0;
        for (int i = 0; i < count; i++) {
            while (skipSundays && day.getDayOfWeek() == DayOfWeek.SUNDAY) {
                day = day.plusDays(1);
            }
            dates.add(day);
            usedToday++;
            if (usedToday == perDay) {
                day = day.plusDays(1);
                usedToday = 0;
            }
        }
        return dates;
    }

    // ---------- Streak ----------

    // Consecutive days (ending today, or yesterday if nothing is done yet today) with at least one DONE topic
    public static int currentStreak(Map<String, Integer> donePerDay, LocalDate today) {
        LocalDate day = today;
        if (donePerDay.getOrDefault(day.toString(), 0) == 0) {
            day = day.minusDays(1);
        }
        int streak = 0;
        while (donePerDay.getOrDefault(day.toString(), 0) > 0) {
            streak++;
            day = day.minusDays(1);
        }
        return streak;
    }

    // ---------- Weak area ----------

    // Rows are {category, total, done}. Returns {category, percentDone, topicsLeft} for the weakest
    // unfinished category, or null if everything is finished or empty.
    public static String[] weakest(List<String[]> categoryRows) {
        String[] best = null;
        double bestPct = 101;
        for (String[] row : categoryRows) {
            int total = Integer.parseInt(row[1]);
            int done = Integer.parseInt(row[2]);
            if (total == 0 || done == total) continue;
            double pct = done * 100.0 / total;
            if (pct < bestPct) {
                bestPct = pct;
                best = new String[]{row[0], String.valueOf((int) Math.round(pct)), String.valueOf(total - done)};
            }
        }
        return best;
    }

    // {category, total, done} rows  ->  category -> {total, done}
    public static Map<String, int[]> toMap(List<String[]> categoryRows) {
        Map<String, int[]> map = new LinkedHashMap<>();
        for (String[] row : categoryRows) {
            map.put(row[0], new int[]{Integer.parseInt(row[1]), Integer.parseInt(row[2])});
        }
        return map;
    }

    // ---------- Company readiness ----------

    public static class Readiness {
        public Long daysLeft;        // null when there is no valid test date
        public int percent;          // % of the test's topics that are DONE
        public int remaining;        // topics still not DONE
        public String weakest;       // category with the lowest progress (null if none)
        public double perDayNeeded;  // remaining topics / days left (0 when unknown)
    }

    // focusCategories like "DSA,Aptitude" (empty = all categories). categoryTotals: category -> {total, done}.
    public static Readiness readiness(String focusCategories, String testDate,
                                      Map<String, int[]> categoryTotals, LocalDate today) {
        Readiness r = new Readiness();

        List<String> focus = new ArrayList<>();
        if (focusCategories != null && !focusCategories.isBlank()) {
            for (String f : focusCategories.split(",")) {
                if (!f.isBlank()) focus.add(f.trim());
            }
        }
        if (focus.isEmpty()) focus.addAll(categoryTotals.keySet());

        int total = 0;
        int done = 0;
        double worst = 101;
        for (String cat : focus) {
            int[] t = categoryTotals.get(cat);
            if (t == null || t[0] == 0) continue;
            total += t[0];
            done += t[1];
            double pct = t[1] * 100.0 / t[0];
            if (pct < worst) {
                worst = pct;
                r.weakest = cat;
            }
        }
        r.percent = total == 0 ? 0 : (int) Math.round(done * 100.0 / total);
        r.remaining = total - done;

        if (testDate != null && !testDate.isBlank()) {
            try {
                r.daysLeft = ChronoUnit.DAYS.between(today, LocalDate.parse(testDate));
                if (r.daysLeft > 0) {
                    r.perDayNeeded = r.remaining / (double) r.daysLeft;
                }
            } catch (DateTimeParseException e) {
                r.daysLeft = null;
            }
        }
        return r;
    }

    // "today", "tomorrow", "in 5 days", "2 days ago"
    public static String daysLabel(String date, LocalDate today) {
        if (date == null || date.isBlank()) return "";
        try {
            long d = ChronoUnit.DAYS.between(today, LocalDate.parse(date));
            if (d == 0) return "today";
            if (d == 1) return "tomorrow";
            if (d > 1) return "in " + d + " days";
            if (d == -1) return "yesterday";
            return Math.abs(d) + " days ago";
        } catch (DateTimeParseException e) {
            return "";
        }
    }
}
