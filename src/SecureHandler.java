import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

// Wraps a page handler: only logged-in users get through, everyone else goes to /login
public class SecureHandler implements HttpHandler {

    private final HttpHandler inner;

    public SecureHandler(HttpHandler inner) {
        this.inner = inner;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        User user;
        try {
            user = Auth.fromRequest(ex);
        } catch (Exception e) {
            WebUtil.sendError(ex, e);
            return;
        }
        if (user == null) {
            WebUtil.redirect(ex, "/login");
            return;
        }
        ex.setAttribute("userId", user.id);
        ex.setAttribute("username", user.username);
        inner.handle(ex);
    }
}
