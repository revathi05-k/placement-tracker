import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {

    public static void main(String[] args) throws IOException {
        Database.init();

        // Hosting services tell the app which port to use through the PORT environment variable
        int port = 8080;
        String portEnv = System.getenv("PORT");
        if (portEnv != null && !portEnv.isBlank()) {
            try {
                port = Integer.parseInt(portEnv.trim());
            } catch (NumberFormatException e) {
                System.out.println("Ignoring invalid PORT value, using 8080.");
            }
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Public pages
        AuthHandler auth = new AuthHandler();
        server.createContext("/login", auth);
        server.createContext("/register", auth);
        server.createContext("/logout", auth);

        server.createContext("/health", ex -> {
            byte[] ok = "ok".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            ex.sendResponseHeaders(200, ok.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(ok);
            }
        });

        // Serves the stylesheet from static/style.css
        server.createContext("/style.css", ex -> {
            byte[] css = Files.readAllBytes(Path.of("static", "style.css"));
            ex.getResponseHeaders().set("Content-Type", "text/css; charset=UTF-8");
            ex.sendResponseHeaders(200, css.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(css);
            }
        });

        // Pages that need a login (SecureHandler redirects visitors to /login)
        server.createContext("/", new SecureHandler(new HomeHandler()));
        server.createContext("/prep", new SecureHandler(new PrepHandler()));
        server.createContext("/companies", new SecureHandler(new CompanyHandler()));
        server.createContext("/planner", new SecureHandler(new PlannerHandler()));
        server.createContext("/export", new SecureHandler(new ExportHandler()));

        server.start();
        System.out.println("Server running. Open http://localhost:" + port + " in your browser.");
    }
}
