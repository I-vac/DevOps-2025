package minitwit;

import static spark.Spark.*;
import com.google.gson.Gson;
import spark.Request;
import spark.Response;
import java.util.*;
import org.mindrot.jbcrypt.BCrypt;

public class SimulatorAPI {
    private static final Gson gson = new Gson();

    public static void main(String[] args) {
        // Configure SparkJava
        port(5001);

        // Database setup
        Database.init();

        // Middleware for content-type and authorization
        before((req, res) -> {
            res.type("application/json");
            String authHeader = req.headers("Authorization");
            if (authHeader == null || !authHeader.startsWith("Basic ")) {
                halt(403, gson.toJson(Map.of("error", "Unauthorized")));
            }
        });

        // Register endpoint
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

        // Post tweet endpoint
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

            // Ensure the user exists in the database before allowing to tweet
            Integer userId = (Integer) user.get("user_id");
            if (userId == null) {
                res.status(404);
                return gson.toJson(Map.of("error", "User ID not found"));
            }

            Database.createMessage(userId, content, System.currentTimeMillis() / 1000);
            res.status(204);
            return "";
        });

        // Follow endpoint
        post("/fllws/:username", (req, res) -> {
            String username = req.params(":username");
            Map<String, Object> payload = gson.fromJson(req.body(), Map.class);
            String followUser = (String) payload.get("follow");

            if (followUser == null) {
                res.status(400);
                return gson.toJson(Map.of("error", "Follow username required"));
            }

            Map<String, Object> user = Database.getUserByUsername(username);
            Map<String, Object> userToFollow = Database.getUserByUsername(followUser);

            if (user == null || userToFollow == null) {
                res.status(404);
                return gson.toJson(Map.of("error", "User not found"));
            }

            Integer userId = (Integer) user.get("user_id");
            Integer followUserId = (Integer) userToFollow.get("user_id");

            if (userId == null || followUserId == null) {
                res.status(404);
                return gson.toJson(Map.of("error", "User ID not found"));
            }

            Database.followUser(userId, followUserId);
            res.status(204);
            return "";
        });

        // Unfollow endpoint
        post("/fllws/:username/unfollow", (req, res) -> {
            String username = req.params(":username");
            Map<String, Object> payload = gson.fromJson(req.body(), Map.class);
            String unfollowUser = (String) payload.get("unfollow");

            if (unfollowUser == null) {
                res.status(400);
                return gson.toJson(Map.of("error", "Unfollow username required"));
            }

            Map<String, Object> user = Database.getUserByUsername(username);
            Map<String, Object> userToUnfollow = Database.getUserByUsername(unfollowUser);

            if (user == null || userToUnfollow == null) {
                res.status(404);
                return gson.toJson(Map.of("error", "User not found"));
            }

            Integer userId = (Integer) user.get("user_id");
            Integer unfollowUserId = (Integer) userToUnfollow.get("user_id");

            if (userId == null || unfollowUserId == null) {
                res.status(404);
                return gson.toJson(Map.of("error", "User ID not found"));
            }

            Database.unfollowUser(userId, unfollowUserId);
            res.status(204);
            return "";
        });

        // Health check endpoint
        get("/health", (req, res) -> gson.toJson(Map.of("status", "API running")));

        // Debug endpoint to validate user existence
        get("/user/:username", (req, res) -> {
            String username = req.params(":username");
            Map<String, Object> user = Database.getUserByUsername(username);
            if (user == null) {
                res.status(404);
                return gson.toJson(Map.of("error", "User not found"));
            }
            return gson.toJson(user);
        });

        // Exception handling
        exception(Exception.class, (e, req, res) -> {
            res.status(500);
            res.body(gson.toJson(Map.of("error", "Unexpected server error", "details", e.getMessage())));
        });

        after((req, res) -> res.type("application/json"));
    }
}
