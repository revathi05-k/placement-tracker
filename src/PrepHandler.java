import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class PrepHandler implements HttpHandler {

    static final String[] CATEGORIES = {"DSA", "Aptitude", "Core CS", "HR", "Project"};
    static final String[] DIFFICULTIES = {"EASY", "MEDIUM", "HARD"};
    static final String[] STATUSES = {"TODO", "IN_PROGRESS", "DONE"};

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            int uid = Auth.userId(ex);
            String path = ex.getRequestURI().getPath();

            if (ex.getRequestMethod().equals("POST")) {
                handlePost(ex, uid, path);
            } else if (path.equals("/prep/edit")) {
                showEditPage(ex, uid);
            } else if (path.equals("/prep/confirm-delete")) {
                showConfirmDelete(ex, uid);
            } else {
                showListPage(ex, uid);
            }
        } catch (Exception e) {
            WebUtil.sendError(ex, e);
        }
    }

    private void handlePost(HttpExchange ex, int uid, String path) throws Exception {
        Map<String, String> f = WebUtil.parseForm(ex.getRequestBody());
        switch (path) {
            case "/prep/add" -> {
                String title = f.getOrDefault("title", "").trim();
                if (!title.isEmpty()) {
                    PrepItemDao.add(uid, title, f.get("category"), f.get("difficulty"),
                            f.get("target_date"), f.get("notes"));
                }
            }
            case "/prep/update" -> {
                String title = f.getOrDefault("title", "").trim();
                if (!title.isEmpty()) {
                    PrepItemDao.update(uid, Integer.parseInt(f.get("id")), title, f.get("category"),
                            f.get("difficulty"), f.get("target_date"), f.get("notes"));
                }
            }
            case "/prep/seed" ->
                PrepItemDao.seedStarter(uid);
            case "/prep/status" ->
                PrepItemDao.updateStatus(uid, Integer.parseInt(f.get("id")), f.get("status"));
            case "/prep/delete" ->
                PrepItemDao.delete(uid, Integer.parseInt(f.get("id")));
            default -> { }
        }
        WebUtil.redirect(ex, "/prep");
    }

    private void showListPage(HttpExchange ex, int uid) throws Exception {
        Map<String, String> q = WebUtil.parseQuery(ex.getRequestURI().getRawQuery());
        String category = q.getOrDefault("category", "");
        String status = q.getOrDefault("status", "");
        String search = q.getOrDefault("q", "").trim();

        List<PrepItem> items = PrepItemDao.findAll(uid, category, status, search);

        StringBuilder sb = new StringBuilder();
        sb.append("<div class='page-head'><h1>Preparation Items</h1>")
          .append("<a class='btn' href='/export/prep.csv'>Download CSV</a></div>");

        // Add form
        sb.append("<form method='post' action='/prep/add' class='card form-grid'>")
          .append("<input name='title' placeholder='Topic, e.g. Binary Search' required>")
          .append("<select name='category'>").append(WebUtil.options(CATEGORIES, "DSA")).append("</select>")
          .append("<select name='difficulty'>").append(WebUtil.options(DIFFICULTIES, "MEDIUM")).append("</select>")
          .append("<input type='date' name='target_date'>")
          .append("<input name='notes' placeholder='Notes (optional)'>")
          .append("<button type='submit'>Add item</button></form>");

        // One-click starter checklist
        sb.append("<form method='post' action='/prep/seed' class='seed-box'>")
          .append("<span>No topics yet? Load about 55 common placement topics in one click. ")
          .append("Topics you already have are not duplicated.</span>")
          .append("<button type='submit' class='secondary'>Load starter checklist</button></form>");

        // Search and filter bar (a GET form: values appear in the URL)
        sb.append("<form method='get' action='/prep' class='card filter-bar'>")
          .append("<input name='q' placeholder='Search topic or notes' value='")
          .append(WebUtil.esc(search)).append("'>")
          .append("<select name='category'>")
          .append(WebUtil.optionsWithAll(CATEGORIES, category, "All categories")).append("</select>")
          .append("<select name='status'>")
          .append(WebUtil.optionsWithAll(STATUSES, status, "All statuses")).append("</select>")
          .append("<button type='submit'>Filter</button>")
          .append("<a class='btn' href='/prep'>Clear</a></form>");

        sb.append("<p class='muted'>Showing ").append(items.size()).append(" item(s)</p>");

        if (items.isEmpty()) {
            sb.append("<p class='card'>No items match. Add one above or clear the filters.</p>");
        } else {
            sb.append("<table><tr><th>Topic</th><th>Category</th><th>Difficulty</th>")
              .append("<th>Target</th><th>Notes</th><th>Status</th><th></th></tr>");
            for (PrepItem p : items) {
                sb.append(p.isOverdue() ? "<tr class='overdue'><td>" : "<tr><td>")
                  .append(WebUtil.esc(p.title))
                  .append(p.isOverdue() ? " <span class='badge rejected'>OVERDUE</span>"
                          : p.isDueToday() ? " <span class='badge in_progress'>DUE TODAY</span>" : "")
                  .append("</td><td>")
                  .append(WebUtil.esc(p.category)).append("</td><td>")
                  .append("<span class='badge ").append(p.difficulty.toLowerCase()).append("'>")
                  .append(p.difficulty).append("</span></td><td>").append("<span class='nowrap'>").append(WebUtil.esc(p.targetDate == null ? "-" : p.targetDate)).append("</span></td><td>")
                  .append(WebUtil.esc(p.notes)).append("</td><td>")
                  .append("<form method='post' action='/prep/status' class='inline'>")
                  .append("<input type='hidden' name='id' value='").append(p.id).append("'>")
                  .append("<select name='status'>").append(WebUtil.options(STATUSES, p.status)).append("</select>")
                  .append("<button type='submit'>Update</button></form></td><td>")
                  .append("<div class='actions'>");
                if (!"DONE".equals(p.status)) {
                    sb.append("<form method='post' action='/prep/status' class='inline'>")
                      .append("<input type='hidden' name='id' value='").append(p.id).append("'>")
                      .append("<input type='hidden' name='status' value='DONE'>")
                      .append("<button type='submit' class='good'>Done</button></form>");
                }
                sb.append("<a class='btn' href='/prep/edit?id=").append(p.id).append("'>Edit</a>")
                  .append("<a class='btn danger' href='/prep/confirm-delete?id=").append(p.id).append("'>Delete</a>")
                  .append("</div></td></tr>");
            }
            sb.append("</table>");
        }

        WebUtil.sendHtml(ex, 200, WebUtil.layout(ex, "Preparation", "prep", sb.toString()));
    }

    private void showEditPage(HttpExchange ex, int uid) throws Exception {
        Map<String, String> q = WebUtil.parseQuery(ex.getRequestURI().getRawQuery());
        PrepItem p = PrepItemDao.findById(uid, Integer.parseInt(q.getOrDefault("id", "0")));
        if (p == null) {
            WebUtil.redirect(ex, "/prep");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<h1>Edit Preparation Item</h1>")
          .append("<form method='post' action='/prep/update' class='card form-grid'>")
          .append("<input type='hidden' name='id' value='").append(p.id).append("'>")
          .append("<label>Topic<input name='title' required value='").append(WebUtil.esc(p.title)).append("'></label>")
          .append("<label>Category<select name='category'>")
          .append(WebUtil.options(CATEGORIES, p.category)).append("</select></label>")
          .append("<label>Difficulty<select name='difficulty'>")
          .append(WebUtil.options(DIFFICULTIES, p.difficulty)).append("</select></label>")
          .append("<label>Target date<input type='date' name='target_date' value='")
          .append(WebUtil.esc(p.targetDate)).append("'></label>")
          .append("<label>Notes<input name='notes' value='").append(WebUtil.esc(p.notes)).append("'></label>")
          .append("<div class='actions'><button type='submit'>Save changes</button>")
          .append("<a class='btn' href='/prep'>Cancel</a></div></form>");

        WebUtil.sendHtml(ex, 200, WebUtil.layout(ex, "Edit item", "prep", sb.toString()));
    }

    // Asks "are you sure?" before deleting (no JavaScript needed: it is just another page)
    private void showConfirmDelete(HttpExchange ex, int uid) throws Exception {
        Map<String, String> q = WebUtil.parseQuery(ex.getRequestURI().getRawQuery());
        PrepItem p = PrepItemDao.findById(uid, Integer.parseInt(q.getOrDefault("id", "0")));
        if (p == null) {
            WebUtil.redirect(ex, "/prep");
            return;
        }
        String body = "<div class='card auth-card'><h1>Delete this topic?</h1>"
                + "<p><strong>" + WebUtil.esc(p.title) + "</strong> will be removed. This cannot be undone.</p>"
                + "<form method='post' action='/prep/delete' class='actions'>"
                + "<input type='hidden' name='id' value='" + p.id + "'>"
                + "<button type='submit' class='danger'>Yes, delete</button>"
                + "<a class='btn' href='/prep'>Cancel</a></form></div>";
        WebUtil.sendHtml(ex, 200, WebUtil.layout(ex, "Delete topic", "prep", body));
    }
}
