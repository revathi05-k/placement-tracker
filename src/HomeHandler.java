import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HomeHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            int uid = Auth.userId(ex);

            if (!ex.getRequestURI().getPath().equals("/")) {
                WebUtil.sendHtml(ex, 404, WebUtil.layout(ex, "Not found", "",
                        "<h1>404 - Page not found</h1><a href='/'>Go to dashboard</a>"));
                return;
            }

            LocalDate today = LocalDate.now();
            int[] prep = PrepItemDao.stats(uid);         // {total, done}
            int[] comp = CompanyDao.stats(uid);          // {total, offers, interviews}
            int percent = prep[0] == 0 ? 0 : (prep[1] * 100) / prep[0];
            List<String[]> cats = PrepItemDao.categorySummary(uid);
            Map<String, Integer> donePerDay = PrepItemDao.completedPerDay(uid, today.minusDays(365).toString());
            int streak = Insights.currentStreak(donePerDay, today);

            StringBuilder sb = new StringBuilder();
            sb.append("<h1>Dashboard</h1><div class='stats'>")
              .append("<div class='card stat'><span>").append(prep[1]).append(" / ").append(prep[0])
              .append("</span>Prep items done</div>")
              .append("<div class='card stat'><span>").append(streak).append("</span>Day streak</div>")
              .append("<div class='card stat'><span>").append(comp[0]).append("</span>Applications</div>")
              .append("<div class='card stat'><span>").append(comp[2]).append("</span>In interview stage</div>")
              .append("<div class='card stat'><span>").append(comp[1]).append("</span>Offers</div></div>");

            sb.append("<div class='card'><strong>Overall preparation: ").append(percent).append("%</strong>")
              .append("<div class='bar'><div class='fill' style='width:").append(percent).append("%'></div></div></div>");

            // Weak-area insight
            String[] weak = Insights.weakest(cats);
            if (weak != null) {
                sb.append("<div class='card insight'><strong>Focus next:</strong> ")
                  .append(WebUtil.esc(weak[0])).append(" is your weakest area (")
                  .append(weak[1]).append("% done, ").append(weak[2]).append(" topic(s) left).</div>");
            }

            appendNeedsAttention(sb, uid);
            appendDrives(sb, uid, cats, today);
            appendWeek(sb, donePerDay, today);
            appendCategories(sb, cats);
            appendStatuses(sb, uid);
            appendUpNext(sb, uid);

            WebUtil.sendHtml(ex, 200, WebUtil.layout(ex, "Dashboard", "home", sb.toString()));
        } catch (Exception e) {
            WebUtil.sendError(ex, e);
        }
    }

    private void appendNeedsAttention(StringBuilder sb, int uid) throws Exception {
        sb.append("<h2>Needs attention</h2>");
        List<PrepItem> due = PrepItemDao.findDue(uid);
        if (due.isEmpty()) {
            sb.append("<p class='card ok'>You're on track: nothing is overdue or due today.</p>");
            return;
        }
        sb.append("<table><tr><th>Topic</th><th>Category</th><th>Target date</th><th></th></tr>");
        boolean anyOverdue = false;
        for (PrepItem item : due) {
            if (item.isOverdue()) anyOverdue = true;
            sb.append(item.isOverdue() ? "<tr class='overdue'><td>" : "<tr><td>")
              .append(WebUtil.esc(item.title)).append("</td><td>")
              .append(WebUtil.esc(item.category)).append("</td><td>")
              .append(WebUtil.esc(item.targetDate)).append("</td><td>")
              .append(item.isOverdue() ? "<span class='badge rejected'>OVERDUE</span>"
                                       : "<span class='badge in_progress'>DUE TODAY</span>")
              .append("</td></tr>");
        }
        sb.append("</table>");
        if (anyOverdue) {
            sb.append("<p><a class='btn' href='/planner'>Re-plan missed topics</a></p>");
        }
    }

    private void appendDrives(StringBuilder sb, int uid, List<String[]> cats, LocalDate today) throws Exception {
        sb.append("<h2>Upcoming company drives</h2>");
        List<Company> drives = CompanyDao.findUpcoming(uid, today.toString());
        if (drives.isEmpty()) {
            sb.append("<p class='card'>No upcoming tests. Add a <em>test date</em> and the topics it covers on the ")
              .append("Companies page to get a readiness score.</p>");
            return;
        }
        Map<String, int[]> totals = Insights.toMap(cats);
        sb.append("<div class='drives'>");
        for (Company c : drives) {
            Insights.Readiness r = Insights.readiness(c.focusCategories, c.testDate, totals, today);
            String level = r.percent >= 70 ? "good" : r.percent >= 40 ? "mid" : "low";
            String covers = (c.focusCategories == null || c.focusCategories.isBlank())
                    ? "all categories" : c.focusCategories.replace(",", ", ");

            sb.append("<div class='card drive'><strong>").append(WebUtil.esc(c.companyName)).append("</strong> ")
              .append("<span class='muted'>").append(WebUtil.esc(c.role)).append("</span>")
              .append("<div class='muted'>Test ").append(WebUtil.esc(Insights.daysLabel(c.testDate, today)))
              .append(" (").append(WebUtil.esc(c.testDate)).append(")</div>")
              .append("<div class='bar'><div class='fill ").append(level).append("' style='width:")
              .append(r.percent).append("%'></div></div>")
              .append("<strong>").append(r.percent).append("% ready</strong>")
              .append("<div class='muted'>Covers: ").append(WebUtil.esc(covers)).append("</div>");

            if (r.remaining == 0) {
                sb.append("<div class='muted'>All topics for this test are done.</div>");
            } else {
                sb.append("<div class='muted'>").append(r.remaining).append(" topic(s) left");
                if (r.weakest != null) {
                    sb.append(". Weakest area: ").append(WebUtil.esc(r.weakest));
                }
                if (r.daysLeft != null && r.daysLeft > 0) {
                    sb.append(". Needs about ")
                      .append(String.format(Locale.ENGLISH, "%.1f", r.perDayNeeded)).append(" topics/day");
                }
                sb.append(".</div>");
            }
            sb.append("</div>");
        }
        sb.append("</div>");
    }

    private void appendWeek(StringBuilder sb, Map<String, Integer> donePerDay, LocalDate today) {
        sb.append("<h2>Last 7 days</h2><div class='card week'>");
        int max = 1;
        for (int i = 6; i >= 0; i--) {
            max = Math.max(max, donePerDay.getOrDefault(today.minusDays(i).toString(), 0));
        }
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            int n = donePerDay.getOrDefault(day.toString(), 0);
            int height = n == 0 ? 3 : 8 + (n * 70) / max;
            sb.append("<div class='col'><span class='num'>").append(n).append("</span>")
              .append("<div class='vbar' style='height:").append(height).append("px'></div>")
              .append("<span class='day'>")
              .append(day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH))
              .append("</span></div>");
        }
        sb.append("</div><p class='muted'>Counts topics you marked DONE on each day.</p>");
    }

    private void appendCategories(StringBuilder sb, List<String[]> cats) {
        sb.append("<h2>Progress by category</h2>");
        if (cats.isEmpty()) {
            sb.append("<p class='card'>No data yet. Load the starter checklist on the Preparation page.</p>");
            return;
        }
        sb.append("<table><tr><th>Category</th><th>Done</th><th>Progress</th></tr>");
        for (String[] row : cats) {
            int total = Integer.parseInt(row[1]);
            int done = Integer.parseInt(row[2]);
            int pct = total == 0 ? 0 : (done * 100) / total;
            sb.append("<tr><td>").append(WebUtil.esc(row[0])).append("</td><td>")
              .append(done).append(" / ").append(total).append("</td><td>")
              .append("<div class='bar'><div class='fill' style='width:").append(pct)
              .append("%'></div></div></td></tr>");
        }
        sb.append("</table>");
    }

    private void appendStatuses(StringBuilder sb, int uid) throws Exception {
        sb.append("<h2>Applications by status</h2><div class='card chips'>");
        List<String[]> statuses = CompanyDao.statusSummary(uid);
        if (statuses.isEmpty()) {
            sb.append("No applications yet.");
        } else {
            for (String[] row : statuses) {
                sb.append("<span class='badge ").append(row[0].toLowerCase()).append("'>")
                  .append(WebUtil.esc(row[0])).append(": ").append(row[1]).append("</span> ");
            }
        }
        sb.append("</div>");
    }

    private void appendUpNext(StringBuilder sb, int uid) throws Exception {
        sb.append("<h2>Up next</h2>");
        List<PrepItem> pending = PrepItemDao.findPending(uid, 5);
        if (pending.isEmpty()) {
            sb.append("<p class='card'>Nothing pending.</p>");
            return;
        }
        sb.append("<table><tr><th>Topic</th><th>Category</th><th>Target date</th><th>Status</th></tr>");
        for (PrepItem p : pending) {
            sb.append("<tr><td>").append(WebUtil.esc(p.title)).append("</td><td>")
              .append(WebUtil.esc(p.category)).append("</td><td>")
              .append(WebUtil.esc(p.targetDate == null ? "-" : p.targetDate)).append("</td><td>")
              .append("<span class='badge ").append(p.status.toLowerCase()).append("'>")
              .append(p.status).append("</span></td></tr>");
        }
        sb.append("</table>");
    }
}
