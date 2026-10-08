import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

public class PlannerHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            int uid = Auth.userId(ex);
            if (ex.getRequestMethod().equals("POST")) {
                handlePost(ex, uid);
            } else {
                showPage(ex, uid);
            }
        } catch (Exception e) {
            WebUtil.sendError(ex, e);
        }
    }

    private void handlePost(HttpExchange ex, int uid) throws Exception {
        Map<String, String> f = WebUtil.parseForm(ex.getRequestBody());

        LocalDate start;
        try {
            start = LocalDate.parse(f.getOrDefault("start", ""));
        } catch (DateTimeParseException e) {
            start = LocalDate.now();
        }

        int perDay = 2;
        try {
            perDay = Integer.parseInt(f.getOrDefault("per_day", "2").trim());
        } catch (NumberFormatException ignored) { }
        if (perDay < 1) perDay = 1;
        if (perDay > 20) perDay = 20;

        boolean skipSundays = f.containsKey("skip_sundays");
        boolean onlyUnplanned = !"all".equals(f.get("mode"));

        List<PrepItem> items = PrepItemDao.findForPlanning(uid, onlyUnplanned);
        List<LocalDate> dates = Insights.assignDates(items.size(), start, perDay, skipSundays);
        PrepItemDao.setTargetDates(uid, items, dates);

        String last = dates.isEmpty() ? "" : dates.get(dates.size() - 1).toString();
        WebUtil.redirect(ex, "/planner?planned=" + items.size() + "&last=" + last);
    }

    private void showPage(HttpExchange ex, int uid) throws Exception {
        Map<String, String> q = WebUtil.parseQuery(ex.getRequestURI().getRawQuery());

        StringBuilder sb = new StringBuilder();
        sb.append("<h1>Study Planner</h1>");

        if (q.containsKey("planned")) {
            String n = q.get("planned");
            if ("0".equals(n)) {
                sb.append("<p class='card insight'>Nothing to plan: there are no matching unfinished topics.</p>");
            } else {
                sb.append("<p class='card ok'>Planned ").append(WebUtil.esc(n))
                  .append(" topic(s). The last one is scheduled for ")
                  .append(WebUtil.esc(q.getOrDefault("last", ""))).append(". ")
                  .append("<a href='/prep'>See the plan</a></p>");
            }
        }

        int unplanned = PrepItemDao.findForPlanning(uid, true).size();
        int unfinished = PrepItemDao.findForPlanning(uid, false).size();

        sb.append("<p class='muted'>Unfinished topics: ").append(unfinished)
          .append(" (").append(unplanned).append(" without a target date)</p>");

        sb.append("<form method='post' action='/planner' class='card form-grid'>")
          .append("<label>Start date<input type='date' name='start' required value='")
          .append(LocalDate.now()).append("'></label>")
          .append("<label>Topics per day<input type='number' name='per_day' min='1' max='20' value='2'></label>")
          .append("<label>Which topics?<select name='mode'>")
          .append("<option value='unplanned'>Only topics without a date</option>")
          .append("<option value='all'>All unfinished topics (re-plan missed ones)</option></select></label>")
          .append("<label class='chk'><input type='checkbox' name='skip_sundays'> Skip Sundays</label>")
          .append("<button type='submit'>Generate plan</button></form>");

        sb.append("<div class='card'><strong>How it works</strong>")
          .append("<p>Unfinished topics are sorted easiest first (EASY, then MEDIUM, then HARD) and given dates ")
          .append("starting from your start date, a fixed number per day.</p>")
          .append("<p>Fell behind? Choose <em>All unfinished topics</em> with today as the start date. ")
          .append("Missed topics are pushed forward and everything is rescheduled.</p></div>");

        WebUtil.sendHtml(ex, 200, WebUtil.layout(ex, "Planner", "planner", sb.toString()));
    }
}
