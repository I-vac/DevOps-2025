package minitwit;

import static spark.Spark.*;
import com.google.gson.Gson;
import spark.Request;
import spark.Response;
import java.util.*;
import org.mindrot.jbcrypt.BCrypt;
import io.prometheus.client.Summary;
import io.prometheus.client.hotspot.DefaultExports;
import io.prometheus.client.exporter.HTTPServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimulatorAPI {
    private static final Logger log = LoggerFactory.getLogger(SimulatorAPI.class);
    private static final int PER_PAGE = 30;
    private static final Gson gson = new Gson();

    // JVM metrics (HotSpot)
    static {
        DefaultExports.initialize();
    }

    // HTTP request latency summary
    private static final Summary httpLatency = Summary.build()
        .name("sim_api_request_duration_seconds")
        .help("Simulator API HTTP request latency in seconds")
        .labelNames("method","endpoint","status")
        .register();

    public static void main(String[] args) throws Exception {
        // Configure SparkJava
        log.info("🔧 Starting Simulator API on port 5001");
        port(5001);
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

        // Database connection is initialized in Database static block

        // Middleware for JSON content-type and simple auth
        before((req, res) -> {
            res.type("application/json");
            String authHeader = req.headers("Authorization");
            if (authHeader == null || !authHeader.startsWith("Basic ")) {
                halt(403, gson.toJson(Map.of("error", "Unauthorized")));
            }
        });

        // Health check endpoint
        get("/health", (req, res) -> gson.toJson(Map.of("status", "ok")));

        // Latest message ID
        get("/latest", (req, res) -> {
            int latest = Database.getLatestCommandId();
            return gson.toJson(Map.of("latest_id", latest));
        });

        // Register user
        post("/register", (req, res) -> {
            Map<String, Object> payload = gson.fromJson(req.body(), Map.class);
            String username = (String) payload.get("username");
            String email = (String) payload.get("email");
            String password = (String) payload.get("pwd");

            if (username == null || email == null || password == null) {
                res.status(400);
                return gson.toJson(Map.of("error", "Missing fields"));
            }

            if (Database.getUserByUsername(username) != null) {
                res.status(400);
                return gson.toJson(Map.of("error", "User already exists"));
            }

            Database.createUser(username, email, BCrypt.hashpw(password, BCrypt.gensalt()));
            res.status(204);
            return "";
        });

        // Post message
        post("/msgs/:username", (req, res) -> {
            String username = req.params(":username");
            Map<String, Object> payload = gson.fromJson(req.body(), Map.class);
            String content = (String) payload.get("content");

            if (content == null || content.trim().isEmpty()) {
                res.status(400);
                return gson.toJson(Map.of("error", "Content cannot be empty"));
            }

            Map<String, Object> user = Database.getUserByUsername(username);
            if (user == null) {
                res.status(404);
                return gson.toJson(Map.of("error", "User not found"));
            }

            Integer userId = (Integer) user.get("id");
            Database.createMessage(userId, content, System.currentTimeMillis() / 1000);
            res.status(204);
            return "";
        });

        // Follow user
        post("/fllws/:username", (req, res) -> {
            String username = req.params(":username");
            Map<String, Object> payload = gson.fromJson(req.body(), Map.class);
            String toFollow = (String) payload.get("follow");

            if (toFollow == null) {
                res.status(400);
                return gson.toJson(Map.of("error", "Follow username required"));
            }

            Map<String, Object> user = Database.getUserByUsername(username);
            Map<String, Object> target = Database.getUserByUsername(toFollow);
            if (user == null || target == null) {
                res.status(404);
                return gson.toJson(Map.of("error", "User not found"));
            }

            Database.followUser((Integer) user.get("id"), (Integer) target.get("id"));
            res.status(204);
            return "";
        });

        // Unfollow user
        post("/fllws/:username/unfollow", (req, res) -> {
            String username = req.params(":username");
            Map<String, Object> payload = gson.fromJson(req.body(), Map.class);
            String toUnfollow = (String) payload.get("unfollow");

            if (toUnfollow == null) {
                res.status(400);
                return gson.toJson(Map.of("error", "Unfollow username required"));
            }

            Map<String, Object> user = Database.getUserByUsername(username);
            Map<String, Object> target = Database.getUserByUsername(toUnfollow);
            if (user == null || target == null) {
                res.status(404);
                return gson.toJson(Map.of("error", "User not found"));
            }

            Database.unfollowUser((Integer) user.get("id"), (Integer) target.get("id"));
            res.status(204);
            return "";
        });

        // Debug: fetch user
        get("/user/:username", (req, res) -> {
            String username = req.params(":username");
            Map<String, Object> user = Database.getUserByUsername(username);
            if (user == null) {
                res.status(404);
                return gson.toJson(Map.of("error", "User not found"));
            }
            return gson.toJson(user);
        });

        // After filter to enforce JSON type
        after((req, res) -> res.type("application/json"));

        // Global exception handler
        exception(Exception.class, (e, req, res) -> {
            log.error("Unhandled exception on {} {}", req.requestMethod(), req.pathInfo(), e);
            res.status(500);
            res.body(gson.toJson(Map.of("error", "Unexpected server error", "details", e.getMessage())));
        });
    }
}
