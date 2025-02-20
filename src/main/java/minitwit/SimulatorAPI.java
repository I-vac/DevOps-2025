package minitwit;

import static spark.Spark.*;
import spark.Request;
import spark.Response;
import java.util.*;
import org.mindrot.jbcrypt.BCrypt;

public class SimulatorAPI {
    private static final int PER_PAGE = 30;

    public static void main(String[] args) {
        // Configure SparkJava
        port(5001);

        // Database setup
        Database.init();

        // Session setup
        before((req, res) -> {
            req.session().maxInactiveInterval(300); // 5 minutes
        });
        
        // Routes
        get("/", (req, res) -> {
            if (req.session().attribute("user_id") == null) {
                res.redirect("/public");
                return null;
            }
            Map<String, Object> model = createModel(req);
            int userId = (Integer) req.session().attribute("user_id");
            model.put("messages", Database.getTimelineMessages(userId, PER_PAGE));
            return TemplateRenderer.render("timeline", model);
        });

        get("/public", (req, res) -> {
            Map<String, Object> model = createModel(req);
            model.put("messages", Database.getPublicTimeline(PER_PAGE));
            return TemplateRenderer.render("timeline", model);
        });

        get("/login", (req, res) -> {
            Map<String, Object> model = createModel(req);
            return TemplateRenderer.render("login", model);
        });

        post("/login", (req, res) -> {
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
            Map<String, Object> model = createModel(req);
            return TemplateRenderer.render("register", model);
        });

        post("/register", (req, res) -> {
            Map<String, Object> model = createModel(req);
            String username = req.queryParams("username");
            String email = req.queryParams("email");
            String password = req.queryParams("password");
            String password2 = req.queryParams("password2");

            // Validation
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
            addFlash(req, "You were logged out");
            req.session().removeAttribute("user_id");
            res.redirect("/public");
            return null;
        });

        get("/:username", (req, res) -> {
            Map<String, Object> model = createModel(req);
            String username = req.params(":username");
            Map<String, Object> profileUser = Database.getUserByUsername(username);

            if (profileUser == null) {
                halt(404, "User not found");
            }

            boolean followed = false;
            Integer currentUserId = req.session().attribute("user_id");
            if (currentUserId != null) {
                followed = Database.isFollowing(currentUserId, (Integer) profileUser.get("user_id"));
            }

            model.put("profile_user", profileUser);
            model.put("followed", followed);
            model.put("messages", Database.getUserTimeline(
                (Integer) profileUser.get("user_id"), PER_PAGE));
            
            return TemplateRenderer.render("timeline", model);
        });

        get("/:username/follow", (req, res) -> {
            Integer currentUserId = checkAuthenticated(req);
            String username = req.params(":username");
            Map<String, Object> profileUser = Database.getUserByUsername(username);

            if (profileUser == null) {
                halt(404, "User not found");
            }

            Database.followUser(currentUserId, (Integer) profileUser.get("user_id"));
            addFlash(req, "You are now following " + username);
            res.redirect("/" + username);
            return null;
        });

        get("/:username/unfollow", (req, res) -> {
            Integer currentUserId = checkAuthenticated(req);
            String username = req.params(":username");
            Map<String, Object> profileUser = Database.getUserByUsername(username);

            if (profileUser == null) {
                halt(404, "User not found");
            }

            Database.unfollowUser(currentUserId, (Integer) profileUser.get("user_id"));
            addFlash(req, "You are no longer following " + username);
            res.redirect("/" + username);
            return null;
        });

        post("/add_message", (req, res) -> {
            Integer userId = checkAuthenticated(req);
            String text = req.queryParams("text");
            
            if (text != null && !text.trim().isEmpty()) {
                Database.createMessage(userId, text.trim(), System.currentTimeMillis() / 1000);
                addFlash(req, "Your message was recorded");
            }
            
            res.redirect("/");
            return null;
        });

        exception(Exception.class, (e, req, res) -> {
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