import com.sun.net.httpserver.HttpExchange;

import java.sql.SQLException;

// Cookie and session helpers
public class Auth {

    private static final String COOKIE = "session";
    // Set the environment variable COOKIE_SECURE=true when the site is served over https
    private static final boolean SECURE = "true".equalsIgnoreCase(System.getenv("COOKIE_SECURE"));

    public static User fromRequest(HttpExchange ex) throws SQLException {
        String token = readCookie(ex, COOKIE);
        if (token == null || token.isEmpty()) return null;
        return UserDao.findBySession(token);
    }

    private static String readCookie(HttpExchange ex, String name) {
        String header = ex.getRequestHeaders().getFirst("Cookie");
        if (header == null) return null;
        for (String part : header.split(";")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) return kv[1];
        }
        return null;
    }

    private static String cookieAttributes(long maxAgeSeconds) {
        return "; Path=/; HttpOnly; SameSite=Lax; Max-Age=" + maxAgeSeconds + (SECURE ? "; Secure" : "");
    }

    // Call before sending the response headers
    public static void startSession(HttpExchange ex, int userId) throws SQLException {
        String token = UserDao.createSession(userId);
        ex.getResponseHeaders().add("Set-Cookie",
                COOKIE + "=" + token + cookieAttributes(UserDao.SESSION_MILLIS / 1000));
    }

    public static void endSession(HttpExchange ex) throws SQLException {
        String token = readCookie(ex, COOKIE);
        if (token != null && !token.isEmpty()) {
            UserDao.deleteSession(token);
        }
        ex.getResponseHeaders().add("Set-Cookie", COOKIE + "=" + cookieAttributes(0));
    }

    // Id of the logged-in user (set by SecureHandler before a page handler runs)
    public static int userId(HttpExchange ex) {
        return (Integer) ex.getAttribute("userId");
    }
}
