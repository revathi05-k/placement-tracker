import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

// Downloads a user's own data as CSV files that open in Excel
public class ExportHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            int uid = Auth.userId(ex);
            String path = ex.getRequestURI().getPath();
            String csv;
            String filename;

            if (path.equals("/export/prep.csv")) {
                csv = prepCsv(uid);
                filename = "prep-items.csv";
            } else if (path.equals("/export/companies.csv")) {
                csv = companyCsv(uid);
                filename = "company-applications.csv";
            } else {
                WebUtil.sendHtml(ex, 404, WebUtil.layout(ex, "Not found", "",
                        "<h1>404 - Page not found</h1><a href='/'>Go to dashboard</a>"));
                return;
            }

            // The leading \uFEFF makes Excel read the file as UTF-8
            byte[] bytes = ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
            ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        } catch (Exception e) {
            WebUtil.sendError(ex, e);
        }
    }

    // Wraps a value in quotes. Values starting with = + - @ get a leading ' so Excel
    // can't run them as formulas (CSV injection).
    private static String cell(String s) {
        if (s == null) return "\"\"";
        String v = s;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    private static void row(StringBuilder sb, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(cell(cells[i]));
        }
        sb.append("\r\n");
    }

    private String prepCsv(int uid) throws Exception {
        StringBuilder sb = new StringBuilder();
        row(sb, "Topic", "Category", "Difficulty", "Status", "Target date", "Completed on", "Notes");
        List<PrepItem> items = PrepItemDao.findAll(uid, "", "", "");
        for (PrepItem p : items) {
            row(sb, p.title, p.category, p.difficulty, p.status, p.targetDate, p.completedAt, p.notes);
        }
        return sb.toString();
    }

    private String companyCsv(int uid) throws Exception {
        StringBuilder sb = new StringBuilder();
        row(sb, "Company", "Role", "Package (LPA)", "Status", "Applied on", "Test date", "Test covers", "Notes");
        List<Company> companies = CompanyDao.findAll(uid, "", "");
        for (Company c : companies) {
            row(sb, c.companyName, c.role, c.packageLpa > 0 ? String.valueOf(c.packageLpa) : "",
                    c.status, c.applicationDate, c.testDate, c.focusCategories, c.notes);
        }
        return sb.toString();
    }
}
