import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Run this file's main() to check the logic in Insights.java. No libraries needed.
public class InsightsTest {

    private static int failures = 0;

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "PASS: " : "FAIL: ") + name);
        if (!ok) failures++;
    }

    public static void main(String[] args) {
        // --- planner ---
        LocalDate monday = LocalDate.of(2026, 10, 5);
        List<LocalDate> plan = Insights.assignDates(5, monday, 2, false);
        check("planner: 2 topics per day",
                plan.get(0).equals(monday) && plan.get(1).equals(monday)
                && plan.get(2).equals(monday.plusDays(1)) && plan.get(4).equals(monday.plusDays(2)));

        LocalDate saturday = LocalDate.of(2026, 10, 3);
        List<LocalDate> noSunday = Insights.assignDates(3, saturday, 1, true);
        check("planner: skips Sunday",
                noSunday.get(0).equals(saturday)
                && noSunday.get(1).equals(LocalDate.of(2026, 10, 5))
                && noSunday.get(2).equals(LocalDate.of(2026, 10, 6)));

        check("planner: no topics gives no dates", Insights.assignDates(0, monday, 2, false).isEmpty());

        // --- streak ---
        LocalDate today = LocalDate.of(2026, 10, 2);
        Map<String, Integer> days = new HashMap<>();
        days.put("2026-10-02", 1);
        days.put("2026-10-01", 2);
        days.put("2026-09-30", 1);
        check("streak: 3 days in a row", Insights.currentStreak(days, today) == 3);

        Map<String, Integer> notYetToday = new HashMap<>();
        notYetToday.put("2026-10-01", 1);
        notYetToday.put("2026-09-30", 1);
        check("streak: still alive if today is empty", Insights.currentStreak(notYetToday, today) == 2);

        Map<String, Integer> gap = new HashMap<>();
        gap.put("2026-10-02", 1);
        gap.put("2026-09-30", 1);
        check("streak: a gap breaks it", Insights.currentStreak(gap, today) == 1);
        check("streak: empty is zero", Insights.currentStreak(new HashMap<>(), today) == 0);

        // --- weakest category ---
        List<String[]> rows = List.of(
                new String[]{"DSA", "10", "2"},
                new String[]{"HR", "5", "5"},
                new String[]{"Aptitude", "10", "6"});
        String[] weak = Insights.weakest(rows);
        check("weakest: picks DSA at 20%, 8 left",
                weak != null && weak[0].equals("DSA") && weak[1].equals("20") && weak[2].equals("8"));
        check("weakest: null when all done",
                Insights.weakest(List.<String[]>of(new String[]{"HR", "5", "5"})) == null);

        // --- readiness ---
        Map<String, int[]> totals = new LinkedHashMap<>();
        totals.put("DSA", new int[]{10, 5});
        totals.put("Aptitude", new int[]{10, 8});
        totals.put("HR", new int[]{5, 5});
        Insights.Readiness r = Insights.readiness("DSA,Aptitude", "2026-10-12", totals, today);
        check("readiness: 65 percent", r.percent == 65);
        check("readiness: 7 topics left", r.remaining == 7);
        check("readiness: weakest is DSA", "DSA".equals(r.weakest));
        check("readiness: 10 days left", r.daysLeft != null && r.daysLeft == 10);
        check("readiness: 0.7 topics per day", Math.abs(r.perDayNeeded - 0.7) < 0.001);

        Insights.Readiness all = Insights.readiness("", null, totals, today);
        check("readiness: empty focus uses all categories", all.percent == 72 && all.daysLeft == null);

        check("daysLabel: today", Insights.daysLabel("2026-10-02", today).equals("today"));
        check("daysLabel: in 5 days", Insights.daysLabel("2026-10-07", today).equals("in 5 days"));

        System.out.println(failures == 0 ? "\nAll checks passed." : "\n" + failures + " check(s) FAILED.");
    }
}
