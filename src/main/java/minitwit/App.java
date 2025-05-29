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

    public static void main(String[] args) throws Exception {
        // Configure SparkJava
        log.info("🔧 Starting MiniTwit on port 5000");
        port(5000);
        staticFiles.location("/public");
        staticFiles.expireTime(600L);

        // Start Prometheus HTTP server on metrics port
        String mp = System.getenv("METRICS_PORT");
        int metricsPort = mp != null ? Integer.parseInt(mp) : 9091;
        new HTTPServer(metricsPort);
        log.info("📈 Prometheus HTTP server running on port {}", metricsPort);

        // before-after filters for HTTP timing
        before((req, res) -> {
            log.info("→ {} {}", req.requestMethod(), req.pathInfo());
            req.attribute("startTime", System.nanoTime());
        });
        afterAfter((req, res) -> {
            long start = (Long) req.attribute("startTime");
            double secs = (System.nanoTime() - start) / 1e9;
            log.info("← {} {} - status={} in {}ms",
                     req.requestMethod(), req.pathInfo(), res.status(), (int)(secs * 1000));
            httpLatency.labels(
                req.requestMethod(), 
                req.pathInfo(), 
                String.valueOf(res.status())
            ).observe(secs);
        });

        // Database connection is initialized in Database.static block
        // (No explicit Database.init() call needed.)

        // Configure Freemarker
        TemplateRenderer.configure();

        // Session setup
        before((req, res) -> req.session().maxInactiveInterval(300));

        // ————————————— Routes —————————————
        get("/", (req, res) -> {
            Integer uid = req.session().attribute("user_id");
            if (uid == null) {
                res.redirect("/public");
                return null;
            }
            Map<String, Object> model = createModel(req);
            model.put("messages", Database.getTimelineMessages(uid, PER_PAGE));
            return TemplateRenderer.render("timeline", model);
        });

        get("/health", (req, res) -> {
            res.type("application/json");
            return new Gson().toJson(Map.of("status", "ok"));
        });

        get("/public", (req, res) -> {
            Map<String, Object> model = createModel(req);
            model.put("messages", Database.getPublicTimeline(PER_PAGE));
            return TemplateRenderer.render("timeline", model);
        });

        get("/login", (req, res) -> {
            return TemplateRenderer.render("login", createModel(req));
        });

        get("/latest", (req, res) -> {
            int latest = Database.getLatestCommandId();
            res.type("application/json");
            return new Gson().toJson(Map.of("latest_id", latest));
        });

        post("/login", (req, res) -> {
            String username = req.queryParams("username");
            String password = req.queryParams("password");
            Map<String,Object> user = Database.getUserByUsername(username);
            if (user != null && BCrypt.checkpw(password, (String)user.get("pw_hash"))) {
                // pull the actual numeric user_id and store it
                Integer uid = ((Number)user.get("user_id")).intValue();
                req.session().attribute("user_id", uid);
                addFlash(req, "You were logged in");
                res.redirect("/");
                return null;
            }
            Map<String,Object> model = createModel(req);
            model.put("error", "Invalid username/password");
            model.put("username", username);
            return TemplateRenderer.render("login", model);
        });

        get("/register", (req, res) -> {
            return TemplateRenderer.render("register", createModel(req));
        });

        post("/register", (req, res) -> {
            Map<String,Object> model = createModel(req);
            String u = req.queryParams("username");
            String e = req.queryParams("email");
            String p = req.queryParams("password");
            String p2 = req.queryParams("password2");
            if (u == null || u.isBlank()) {
                model.put("error", "You have to enter a username");
            } else if (e == null || !e.contains("@")) {
                model.put("error", "You have to enter a valid email address");
            } else if (p == null || p.isBlank()) {
                model.put("error", "You have to enter a password");
            } else if (!p.equals(p2)) {
                model.put("error", "The two passwords do not match");
            } else if (Database.getUserByUsername(u) != null) {
                model.put("error", "The username is already taken");
            }
            if (model.containsKey("error")) {
                model.put("username", u);
                model.put("email", e);
                return TemplateRenderer.render("register", model);
            }
            Database.createUser(u, e, BCrypt.hashpw(p, BCrypt.gensalt()));
            addFlash(req, "You were successfully registered and can login now");
            res.redirect("/login");
            return null;
        });

        get("/logout", (req, res) -> {
            addFlash(req, "You were logged out");
            req.session().removeAttribute("user_id");
            res.redirect("/public");
            return null;
        });

        get("/:username", (req, res) -> {
            String profileUser = req.params(":username");
            Map<String,Object> profile = Database.getUserByUsername(profileUser);
            if (profile == null) halt(404, "User not found");
            Integer current = req.session().attribute("user_id");
            boolean followed = current != null 
                && Database.isFollowing(current, (Integer)profile.get("id"));
            Map<String,Object> model = createModel(req);
            model.put("profile_user", profile);
            model.put("followed", followed);
            model.put("messages", Database.getUserTimeline((Integer)profile.get("id"), PER_PAGE));
            return TemplateRenderer.render("timeline", model);
        });

        get("/:username/follow", (req, res) -> {
            Integer current = checkAuthenticated(req);
            String userToFollow = req.params(":username");
            Map<String,Object> profile = Database.getUserByUsername(userToFollow);
            if (profile == null) halt(404, "User not found");
            Database.followUser(current, (Integer)profile.get("id"));
            addFlash(req, "You are now following " + userToFollow);
            res.redirect("/" + userToFollow);
            return null;
        });

        get("/:username/unfollow", (req, res) -> {
            Integer current = checkAuthenticated(req);
            String userToUnfollow = req.params(":username");
            Map<String,Object> profile = Database.getUserByUsername(userToUnfollow);
            if (profile == null) halt(404, "User not found");
            Database.unfollowUser(current, (Integer)profile.get("id"));
            addFlash(req, "You are no longer following " + userToUnfollow);
            res.redirect("/" + userToUnfollow);
            return null;
        });

        post("/add_message", (req, res) -> {
            Integer user = checkAuthenticated(req);
            String text = req.queryParams("text");
            if (text != null && !text.isBlank()) {
                Database.createMessage(user, text.trim(), System.currentTimeMillis() / 1000);
                addFlash(req, "Your message was recorded");
            }
            res.redirect("/");
            return null;
        });

        // Global exception handler
        exception(Exception.class, (e, req, res) -> {
            log.error("Unhandled exception on {} {}", req.requestMethod(), req.pathInfo(), e);
            res.status(500);
            res.body("Internal Server Error: " + e.getMessage());
        });

        init(); // Start Spark
    }

    private static Map<String,Object> createModel(Request req) {
        Map<String,Object> model = new HashMap<>();
        Integer uid = req.session().attribute("user_id");
        if (uid != null) {
            model.put("user", Database.getUserById(uid));
        }
        @SuppressWarnings("unchecked")
        List<String> flashes = req.session().attribute("flashes");
        if (flashes != null && !flashes.isEmpty()) {
            model.put("flashes", new ArrayList<>(flashes));
            req.session().removeAttribute("flashes");
        }
        return model;
    }

    private static void addFlash(Request req, String msg) {
        @SuppressWarnings("unchecked")
        List<String> f = req.session().attribute("flashes");
        if (f == null) {
            f = new ArrayList<>();
        }
        f.add(msg);
        req.session().attribute("flashes", f);
    }

    private static Integer checkAuthenticated(Request req) {
        Integer uid = req.session().attribute("user_id");
        if (uid == null) {
            halt(401, "Unauthorized");
        }
        return uid;
    }
}
