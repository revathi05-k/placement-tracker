import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Handles /login, /register and /logout
public class AuthHandler implements HttpHandler {

    // Simple brute-force protection: too many wrong passwords for a username locks it for a while
    private static final Map<String, long[]> FAILS = new ConcurrentHashMap<>();   // username -> {count, windowStart}
    private static final int MAX_FAILS = 10;
    private static final long WINDOW_MS = 15 * 60 * 1000L;

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            boolean post = ex.getRequestMethod().equals("POST");

            if (path.equals("/logout")) {
                if (post) Auth.endSession(ex);
                WebUtil.redirect(ex, "/login");
                return;
            }

            if (!post && Auth.fromRequest(ex) != null) {
                WebUtil.redirect(ex, "/");
                return;
            }

            if (path.equals("/register")) {
                if (post) doRegister(ex); else showRegister(ex, "", "");
            } else {
                if (post) doLogin(ex); else showLogin(ex, "", "");
            }
        } catch (Exception e) {
            WebUtil.sendError(ex, e);
        }
    }

    private boolean isLocked(String key) {
        long[] f = FAILS.get(key);
        if (f == null) return false;
        if (System.currentTimeMillis() - f[1] > WINDOW_MS) {
            FAILS.remove(key);
            return false;
        }
        return f[0] >= MAX_FAILS;
    }

    private void recordFailure(String key) {
        long now = System.currentTimeMillis();
        FAILS.compute(key, (k, f) -> {
            if (f == null || now - f[1] > WINDOW_MS) return new long[]{1, now};
            f[0]++;
            return f;
        });
    }

    private void doLogin(HttpExchange ex) throws Exception {
        Map<String, String> f = WebUtil.parseForm(ex.getRequestBody());
        String username = f.getOrDefault("username", "").trim().toLowerCase();
        String password = f.getOrDefault("password", "");

        if (isLocked(username)) {
            showLogin(ex, "Too many failed attempts. Please wait 15 minutes and try again.", username);
            return;
        }
        User user = UserDao.authenticate(username, password);
        if (user == null) {
            recordFailure(username);
            showLogin(ex, "Wrong username or password.", username);
            return;
        }
        FAILS.remove(username);
        Auth.startSession(ex, user.id);
        WebUtil.redirect(ex, "/");
    }

    private void doRegister(HttpExchange ex) throws Exception {
        Map<String, String> f = WebUtil.parseForm(ex.getRequestBody());
        String username = f.getOrDefault("username", "").trim().toLowerCase();
        String password = f.getOrDefault("password", "");
        String confirm = f.getOrDefault("confirm", "");

        String error = null;
        if (!username.matches("[a-z0-9_.-]{3,30}")) {
            error = "Username must be 3 to 30 characters: letters, numbers, dot, dash or underscore.";
        } else if (password.length() < 8) {
            error = "Password must be at least 8 characters.";
        } else if (password.length() > 128) {
            error = "Password is too long (maximum 128 characters).";
        } else if (!password.equals(confirm)) {
            error = "The two passwords do not match.";
        }
        if (error != null) {
            showRegister(ex, error, username);
            return;
        }

        int id = UserDao.create(username, password);
        if (id < 0) {
            showRegister(ex, "That username is already taken.", username);
            return;
        }
        Auth.startSession(ex, id);
        WebUtil.redirect(ex, "/");
    }

    private void showLogin(HttpExchange ex, String error, String username) throws IOException {
        StringBuilder sb = new StringBuilder("<div class='card auth-card'><h1>Log in</h1>");
        if (!error.isEmpty()) {
            sb.append("<p class='error'>").append(WebUtil.esc(error)).append("</p>");
        }
        sb.append("<form method='post' action='/login' class='stack'>")
          .append("<label>Username<input name='username' required autofocus value='")
          .append(WebUtil.esc(username)).append("'></label>")
          .append("<label>Password<input type='password' name='password' required></label>")
          .append("<button type='submit'>Log in</button></form>")
          .append("<p class='muted'>New here? <a href='/register'>Create an account</a></p></div>");
        WebUtil.sendHtml(ex, 200, WebUtil.layout(ex, "Log in", "", sb.toString()));
    }

    private void showRegister(HttpExchange ex, String error, String username) throws Exception {
        StringBuilder sb = new StringBuilder("<div class='card auth-card'><h1>Create account</h1>");
        if (!error.isEmpty()) {
            sb.append("<p class='error'>").append(WebUtil.esc(error)).append("</p>");
        }
        if (UserDao.count() == 0) {
            sb.append("<p class='muted'>You are the first user. Any data already in this database ")
              .append("will become yours.</p>");
        }
        sb.append("<form method='post' action='/register' class='stack'>")
          .append("<label>Username<input name='username' required autofocus value='")
          .append(WebUtil.esc(username)).append("'></label>")
          .append("<label>Password (at least 8 characters)<input type='password' name='password' required></label>")
          .append("<label>Confirm password<input type='password' name='confirm' required></label>")
          .append("<button type='submit'>Create account</button></form>")
          .append("<p class='muted'>Already have an account? <a href='/login'>Log in</a></p></div>");
        WebUtil.sendHtml(ex, 200, WebUtil.layout(ex, "Create account", "", sb.toString()));
    }
}
