import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class WebUtil {

    // Turns "a=1&b=hello+world" into a Map. Works for form bodies and URL queries.
    public static Map<String, String> parseQuery(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null || raw.isEmpty()) return map;
        for (String pair : raw.split("&")) {
            String[] kv = pair.split("=", 2);
            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String val = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            map.put(key, val);
        }
        return map;
    }

    // Reads a submitted HTML form body into a Map
    public static Map<String, String> parseForm(InputStream in) throws IOException {
        return parseQuery(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    }

    // Stops user text from breaking the page (or injecting HTML)
    public static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    public static void sendHtml(HttpExchange ex, int code, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static void redirect(HttpExchange ex, String location) throws IOException {
        ex.getResponseHeaders().set("Location", location);
        ex.sendResponseHeaders(303, -1);
        ex.close();
    }

    // Builds <option> tags for a dropdown
    public static String options(String[] values, String selected) {
        StringBuilder sb = new StringBuilder();
        for (String v : values) {
            sb.append("<option value='").append(esc(v)).append("'")
              .append(v.equals(selected) ? " selected" : "")
              .append(">").append(esc(v)).append("</option>");
        }
        return sb.toString();
    }

    // Same, with a first "All" option (used by the filter bars)
    public static String optionsWithAll(String[] values, String selected, String allLabel) {
        return "<option value=''>" + esc(allLabel) + "</option>" + options(values, selected);
    }

    private static String navLink(String href, String label, boolean active) {
        return "<a href='" + href + "'" + (active ? " class='active'" : "") + ">" + label + "</a>";
    }

    // The common page frame. Logged-in pages show the menu and a log out button.
    public static String layout(HttpExchange ex, String title, String active, String body) {
        String username = (String) ex.getAttribute("username");

        StringBuilder nav = new StringBuilder("<nav><span class='brand'>Placement Tracker</span>");
        if (username != null) {
            nav.append(navLink("/", "Dashboard", active.equals("home")))
               .append(navLink("/prep", "Preparation", active.equals("prep")))
               .append(navLink("/planner", "Planner", active.equals("planner")))
               .append(navLink("/companies", "Companies", active.equals("companies")))
               .append("<span class='spacer'></span><span class='user'>").append(esc(username)).append("</span>")
               .append("<form method='post' action='/logout' class='inline'>")
               .append("<button type='submit' class='link-btn'>Log out</button></form>");
        }
        nav.append("</nav>");

        return "<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'>"
             + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
             + "<title>" + esc(title) + " - Placement Tracker</title>"
             + "<link rel='stylesheet' href='/style.css'></head><body>"
             + nav + "<main>" + body + "</main></body></html>";
    }

    public static void sendError(HttpExchange ex, Exception e) throws IOException {
        e.printStackTrace();
        sendHtml(ex, 500, layout(ex, "Error", "", "<h1>Something went wrong</h1><p class='card'>"
                + esc(e.getMessage()) + "</p><a href='/'>Back to dashboard</a>"));
    }
}
