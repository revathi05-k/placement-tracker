import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CompanyHandler implements HttpHandler {

    static final String[] STATUSES = {"APPLIED", "TEST", "INTERVIEW", "OFFER", "REJECTED"};

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            int uid = Auth.userId(ex);
            String path = ex.getRequestURI().getPath();

            if (ex.getRequestMethod().equals("POST")) {
                handlePost(ex, uid, path);
            } else if (path.equals("/companies/edit")) {
                showEditPage(ex, uid);
            } else if (path.equals("/companies/confirm-delete")) {
                showConfirmDelete(ex, uid);
            } else {
                showListPage(ex, uid);
            }
        } catch (Exception e) {
            WebUtil.sendError(ex, e);
        }
    }

    private double parseLpa(String s) {
        try {
            return Double.parseDouble(s == null ? "0" : s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // Checkboxes are named f_DSA, f_Aptitude, ... so each one is a separate form field
    private String focusFromForm(Map<String, String> f) {
        List<String> picked = new ArrayList<>();
        for (String cat : PrepHandler.CATEGORIES) {
            if (f.containsKey("f_" + cat)) picked.add(cat);
        }
        return String.join(",", picked);
    }

    private String focusBoxes(String selectedCsv) {
        List<String> selected = new ArrayList<>();
        if (selectedCsv != null) {
            for (String s : selectedCsv.split(",")) selected.add(s.trim());
        }
        StringBuilder sb = new StringBuilder("<fieldset class='focus-box'>")
                .append("<legend>Test covers (used for the readiness score)</legend>");
        for (String cat : PrepHandler.CATEGORIES) {
            sb.append("<label class='chk'><input type='checkbox' name='f_").append(WebUtil.esc(cat)).append("'")
              .append(selected.contains(cat) ? " checked" : "")
              .append("> ").append(WebUtil.esc(cat)).append("</label>");
        }
        return sb.append("</fieldset>").toString();
    }

    private void handlePost(HttpExchange ex, int uid, String path) throws Exception {
        Map<String, String> f = WebUtil.parseForm(ex.getRequestBody());
        switch (path) {
            case "/companies/add" -> {
                String name = f.getOrDefault("company_name", "").trim();
                String role = f.getOrDefault("role", "").trim();
                if (!name.isEmpty() && !role.isEmpty()) {
                    CompanyDao.add(uid, name, role, parseLpa(f.get("package_lpa")), f.get("status"),
                            f.get("application_date"), f.get("test_date"), focusFromForm(f), f.get("notes"));
                }
            }
            case "/companies/update" -> {
                String name = f.getOrDefault("company_name", "").trim();
                String role = f.getOrDefault("role", "").trim();
                if (!name.isEmpty() && !role.isEmpty()) {
                    CompanyDao.update(uid, Integer.parseInt(f.get("id")), name, role,
                            parseLpa(f.get("package_lpa")), f.get("status"), f.get("application_date"),
                            f.get("test_date"), focusFromForm(f), f.get("notes"));
                }
            }
            case "/companies/status" ->
                CompanyDao.updateStatus(uid, Integer.parseInt(f.get("id")), f.get("status"));
            case "/companies/delete" ->
                CompanyDao.delete(uid, Integer.parseInt(f.get("id")));
            default -> { }
        }
        WebUtil.redirect(ex, "/companies");
    }

    private String testCell(Company c, LocalDate today) {
        if (c.testDate == null || c.testDate.isBlank()) return "-";
        StringBuilder sb = new StringBuilder(WebUtil.esc(c.testDate));
        String label = Insights.daysLabel(c.testDate, today);
        if (!label.isEmpty()) sb.append("<br><span class='muted'>").append(WebUtil.esc(label)).append("</span>");
        if (c.focusCategories != null && !c.focusCategories.isBlank()) {
            sb.append("<br><span class='muted'>Covers: ")
              .append(WebUtil.esc(c.focusCategories.replace(",", ", "))).append("</span>");
        }
        return sb.toString();
    }

    private void showListPage(HttpExchange ex, int uid) throws Exception {
        Map<String, String> q = WebUtil.parseQuery(ex.getRequestURI().getRawQuery());
        String status = q.getOrDefault("status", "");
        String search = q.getOrDefault("q", "").trim();
        LocalDate today = LocalDate.now();

        List<Company> companies = CompanyDao.findAll(uid, status, search);

        StringBuilder sb = new StringBuilder();
        sb.append("<div class='page-head'><h1>Company Applications</h1>")
          .append("<a class='btn' href='/export/companies.csv'>Download CSV</a></div>");

        sb.append("<form method='post' action='/companies/add' class='card form-grid'>")
          .append("<input name='company_name' placeholder='Company, e.g. Infosys' required>")
          .append("<input name='role' placeholder='Role' required>")
          .append("<input type='number' step='0.1' min='0' name='package_lpa' placeholder='Package (LPA)'>")
          .append("<select name='status'>").append(WebUtil.options(STATUSES, "APPLIED")).append("</select>")
          .append("<label>Applied on<input type='date' name='application_date'></label>")
          .append("<label>Test / drive date<input type='date' name='test_date'></label>")
          .append("<input name='notes' placeholder='Notes (optional)'>")
          .append(focusBoxes(""))
          .append("<button type='submit'>Add company</button></form>");

        sb.append("<form method='get' action='/companies' class='card filter-bar'>")
          .append("<input name='q' placeholder='Search company or role' value='")
          .append(WebUtil.esc(search)).append("'>")
          .append("<select name='status'>")
          .append(WebUtil.optionsWithAll(STATUSES, status, "All statuses")).append("</select>")
          .append("<button type='submit'>Filter</button>")
          .append("<a class='btn' href='/companies'>Clear</a></form>");

        sb.append("<p class='muted'>Showing ").append(companies.size()).append(" application(s)</p>");

        if (companies.isEmpty()) {
            sb.append("<p class='card'>No applications match. Add one above or clear the filters.</p>");
        } else {
            sb.append("<table><tr><th>Company</th><th>Package</th><th>Applied on</th>")
              .append("<th>Test date</th><th>Notes</th><th>Status</th><th></th></tr>");
            for (Company c : companies) {
                sb.append("<tr><td><strong>").append(WebUtil.esc(c.companyName)).append("</strong><br>")
                  .append("<span class='muted'>").append(WebUtil.esc(c.role)).append("</span></td><td>")
                  .append(c.packageLpa > 0 ? c.packageLpa + " LPA" : "-").append("</td><td>")
                  .append(WebUtil.esc(c.applicationDate == null ? "-" : c.applicationDate)).append("</td><td>")
                  .append(testCell(c, today)).append("</td><td>")
                  .append(WebUtil.esc(c.notes)).append("</td><td>")
                  .append("<form method='post' action='/companies/status' class='inline'>")
                  .append("<input type='hidden' name='id' value='").append(c.id).append("'>")
                  .append("<select name='status'>").append(WebUtil.options(STATUSES, c.status)).append("</select>")
                  .append("<button type='submit'>Update</button></form></td><td>")
                  .append("<div class='actions'><a class='btn' href='/companies/edit?id=").append(c.id).append("'>Edit</a>")
                  .append("<a class='btn danger' href='/companies/confirm-delete?id=").append(c.id).append("'>Delete</a>")
                  .append("</div></td></tr>");
            }
            sb.append("</table>");
        }

        WebUtil.sendHtml(ex, 200, WebUtil.layout(ex, "Companies", "companies", sb.toString()));
    }

    private void showEditPage(HttpExchange ex, int uid) throws Exception {
        Map<String, String> q = WebUtil.parseQuery(ex.getRequestURI().getRawQuery());
        Company c = CompanyDao.findById(uid, Integer.parseInt(q.getOrDefault("id", "0")));
        if (c == null) {
            WebUtil.redirect(ex, "/companies");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<h1>Edit Company Application</h1>")
          .append("<form method='post' action='/companies/update' class='card form-grid'>")
          .append("<input type='hidden' name='id' value='").append(c.id).append("'>")
          .append("<label>Company<input name='company_name' required value='")
          .append(WebUtil.esc(c.companyName)).append("'></label>")
          .append("<label>Role<input name='role' required value='").append(WebUtil.esc(c.role)).append("'></label>")
          .append("<label>Package (LPA)<input type='number' step='0.1' min='0' name='package_lpa' value='")
          .append(c.packageLpa > 0 ? String.valueOf(c.packageLpa) : "").append("'></label>")
          .append("<label>Status<select name='status'>")
          .append(WebUtil.options(STATUSES, c.status)).append("</select></label>")
          .append("<label>Applied on<input type='date' name='application_date' value='")
          .append(WebUtil.esc(c.applicationDate)).append("'></label>")
          .append("<label>Test / drive date<input type='date' name='test_date' value='")
          .append(WebUtil.esc(c.testDate)).append("'></label>")
          .append("<label>Notes<input name='notes' value='").append(WebUtil.esc(c.notes)).append("'></label>")
          .append(focusBoxes(c.focusCategories))
          .append("<div class='actions'><button type='submit'>Save changes</button>")
          .append("<a class='btn' href='/companies'>Cancel</a></div></form>");

        WebUtil.sendHtml(ex, 200, WebUtil.layout(ex, "Edit company", "companies", sb.toString()));
    }

    private void showConfirmDelete(HttpExchange ex, int uid) throws Exception {
        Map<String, String> q = WebUtil.parseQuery(ex.getRequestURI().getRawQuery());
        Company c = CompanyDao.findById(uid, Integer.parseInt(q.getOrDefault("id", "0")));
        if (c == null) {
            WebUtil.redirect(ex, "/companies");
            return;
        }
        String body = "<div class='card auth-card'><h1>Delete this application?</h1>"
                + "<p><strong>" + WebUtil.esc(c.companyName) + "</strong> (" + WebUtil.esc(c.role)
                + ") will be removed. This cannot be undone.</p>"
                + "<form method='post' action='/companies/delete' class='actions'>"
                + "<input type='hidden' name='id' value='" + c.id + "'>"
                + "<button type='submit' class='danger'>Yes, delete</button>"
                + "<a class='btn' href='/companies'>Cancel</a></form></div>";
        WebUtil.sendHtml(ex, 200, WebUtil.layout(ex, "Delete application", "companies", body));
    }
}
