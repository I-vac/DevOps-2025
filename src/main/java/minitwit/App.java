package minitwit;

import static spark.Spark.*;
import spark.Request;
import spark.Response;
import java.util.*;
import org.mindrot.jbcrypt.BCrypt;
import com.google.gson.Gson;

import io.prometheus.client.Summary;
import io.prometheus.client.hotspot.DefaultExports;
import io.prometheus.client.exporter.HTTPServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class App {
    private static final Logger log = LoggerFactory.getLogger(App.class);

    private static final int PER_PAGE = 30;

    // 1️⃣ JVM metrics (HotSpot)
    static {
        DefaultExports.initialize();
    }

    // 2️⃣ HTTP request latency summary
    static final Summary httpLatency = Summary.build()
        .name("http_request_duration_seconds")
        .help("HTTP request latency in seconds")
        .labelNames("method","endpoint","status")
        .register();

    // 3️⃣ DB query latency summary (DB instrumentation in Database class)

    public static void main(String[] args) throws Exception {
        // Configure SparkJava
        log.info("Starting Minitwit on port 5000");
        port(5000);
        staticFiles.location("/public");
        staticFiles.expireTime(600L);

        // Start Prometheus HTTP server on metrics port
        String mp = System.getenv("METRICS_PORT");
        int metricsPort = mp != null ? Integer.parseInt(mp) : 9091;
        new HTTPServer(metricsPort);
        log.info("Prometheus HTTP server running on port {}", metricsPort);

        // before-after filters for HTTP timing
        before((req, res) -> {
            log.debug("→ {} {}", req.requestMethod(), req.pathInfo());
            req.attribute("startTime", System.nanoTime());
        });
        afterAfter((req, res) -> {
            long start = (Long) req.attribute("startTime");
            double secs = (System.nanoTime() - start) / 1e9;
            log.debug("← {} {} - status={} in {}ms",
                        req.requestMethod(), req.pathInfo(), res.status(), (int)(secs*1000));
            httpLatency.labels(req.requestMethod(), req.pathInfo(), 
                                String.valueOf(res.status())).observe(secs);
        });


        // Database setup
        Database.init();

        // Configure Freemarker
        TemplateRenderer.configure();

        // Session setup
        before((req, res) -> req.session().maxInactiveInterval(300));

        // Routes
        get("/", (req, res) -> {
            if (req.session().attribute("user_id") == null) {
                res.redirect("/public");
                return null;
            }
            Map<String, Object> model = createModel(req);
            int userId = req.session().attribute("user_id");
            model.put("messages", Database.getTimelineMessages(userId, PER_PAGE));
            return TemplateRenderer.render("timeline", model);
        });

        // Health check
        get("/health", (req, res) -> {
            log.info("Health check requested");
            res.type("application/json");
            return new Gson().toJson(Map.of("status", "ok"));
        });

        get("/public", (req, res) -> {
            log.info("Public timeline requested");
            Map<String, Object> model = createModel(req);
            model.put("messages", Database.getPublicTimeline(PER_PAGE));
            return TemplateRenderer.render("timeline", model);
        });

        get("/login", (req, res) -> {
            log.info("Login page requested");
            Map<String, Object> model = createModel(req);
            return TemplateRenderer.render("login", model);
        });

        get("/latest", (req, res) -> {
            log.info("Latest command ID requested");
            int latest = Database.getLatestCommandId();
            res.type("application/json");
            return new Gson().toJson(Map.of("latest_id", latest));
        });

        post("/login", (req, res) -> {
            log.info("Login attempt for user: {}", req.queryParams("username"));
            String username = req.queryParams("username");
            String password = req.queryParams("password");
            Map<String, Object> user = Database.getUserByUsername(username);
            if (user != null && BCrypt.checkpw(password, (String) user.get("pw_hash"))) {
                req.session().attribute("user_id", user.get("user_id"));
                addFlash(req, "You were logged in");
                res.redirect("/");
                return null;
            }
            Map<String, Object> model = createModel(req);
            model.put("error", "Invalid username/password");
            model.put("username", username);
            return TemplateRenderer.render("login", model);
        });

        get("/register", (req, res) -> {
            log.info("Register page requested");
            Map<String, Object> model = createModel(req);
            return TemplateRenderer.render("register", model);
        });

        post("/register", (req, res) -> {
            log.info("Registration attempt for user: {}", req.queryParams("username"));
            Map<String, Object> model = createModel(req);
            String username = req.queryParams("username");
            String email = req.queryParams("email");
            String password = req.queryParams("password");
            String password2 = req.queryParams("password2");
            if (username == null || username.isEmpty()) {
                model.put("error", "You have to enter a username");
            } else if (email == null || !email.contains("@")) {
                model.put("error", "You have to enter a valid email address");
            } else if (password == null || password.isEmpty()) {
                model.put("error", "You have to enter a password");
            } else if (!password.equals(password2)) {
                model.put("error", "The two passwords do not match");
            } else if (Database.getUserByUsername(username) != null) {
                model.put("error", "The username is already taken");
            }
            if (model.containsKey("error")) {
                model.put("username", username);
                model.put("email", email);
                return TemplateRenderer.render("register", model);
            }
            Database.createUser(username, email, BCrypt.hashpw(password, BCrypt.gensalt()));
            addFlash(req, "You were successfully registered and can login now");
            res.redirect("/login");
            return null;
        });

        get("/logout", (req, res) -> {
            log.info("Logout requested");
            addFlash(req, "You were logged out");
            req.session().removeAttribute("user_id");
            res.redirect("/public");
            return null;
        });

        get("/:username", (req, res) -> {
            log.info("Profile requested for user: {}", req.params(":username"));
            Map<String, Object> model = createModel(req);
            String username = req.params(":username");
            Map<String, Object> profile = Database.getUserByUsername(username);
            if (profile == null) halt(404, "User not found");
            boolean followed = false;
            Integer current = req.session().attribute("user_id");
            if (current != null) {
                followed = Database.isFollowing(current, (Integer) profile.get("user_id"));
            }
            model.put("profile_user", profile);
            model.put("followed", followed);
            model.put("messages", Database.getUserTimeline((Integer) profile.get("user_id"), PER_PAGE));
            return TemplateRenderer.render("timeline", model);
        });

        get("/:username/follow", (req, res) -> {
            log.info("Follow request for user: {}", req.params(":username"));
            Integer current = checkAuthenticated(req);
            String username = req.params(":username");
            Map<String, Object> profile = Database.getUserByUsername(username);
            if (profile == null) halt(404, "User not found");
            Database.followUser(current, (Integer) profile.get("user_id"));
            addFlash(req, "You are now following " + username);
            res.redirect("/" + username);
            return null;
        });

        get("/:username/unfollow", (req, res) -> {
            log.info("Unfollow request for user: {}", req.params(":username"));
            Integer current = checkAuthenticated(req);
            String username = req.params(":username");
            Map<String, Object> profile = Database.getUserByUsername(username);
            if (profile == null) halt(404, "User not found");
            Database.unfollowUser(current, (Integer) profile.get("user_id"));
            addFlash(req, "You are no longer following " + username);
            res.redirect("/" + username);
            return null;
        });

        post("/add_message", (req, res) -> {
            log.info("Add message request from user: {}", req.session().attribute("user_id"));
            Integer user = checkAuthenticated(req);
            String text = req.queryParams("text");
            if (text != null && !text.trim().isEmpty()) {
                Database.createMessage(user, text.trim(), System.currentTimeMillis()/1000);
                addFlash(req, "Your message was recorded");
            }
            res.redirect("/");
            return null;
        });

        exception(Exception.class, (e, req, res) -> {
            log.error("Unhandled exception on {} {}", req.requestMethod(), req.pathInfo(), e);
            res.status(500);
            res.body("Internal Server Error: " + e.getMessage());
        });
    }

    private static Map<String, Object> createModel(Request req) {
        Map<String, Object> model = new HashMap<>();
        Integer userId = req.session().attribute("user_id");
        
        if (userId != null) {
            model.put("user", Database.getUserById(userId));
        }
        
        List<String> flashes = req.session().attribute("flashes");
        if (flashes != null && !flashes.isEmpty()) {
            model.put("flashes", new ArrayList<>(flashes));
            req.session().removeAttribute("flashes");
        }
        
        return model;
    }

    private static void addFlash(Request req, String message) {
        List<String> flashes = req.session().attribute("flashes");
        if (flashes == null) {
            flashes = new ArrayList<>();
        }
        flashes.add(message);
        req.session().attribute("flashes", flashes);
    }

    private static Integer checkAuthenticated(Request req) {
        Integer userId = req.session().attribute("user_id");
        if (userId == null) {
            halt(401, "Unauthorized");
        }
        return userId;
    }
}