package minitwit;

import io.prometheus.client.Summary;
import java.sql.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Database {
    private static final Logger log = LoggerFactory.getLogger(Database.class);
    private static Connection connection;
    public static final int PER_PAGE = 30;

    // ──────────────────────────────────────────────────────────────────────────
    // Load driver & open connection once, at class‐load time
    // ──────────────────────────────────────────────────────────────────────────
    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            String jdbcUrl = System.getenv("DATABASE_URL");
            String dbUser  = System.getenv("DB_USER");
            String dbPass  = System.getenv("DB_PASS");
            if (jdbcUrl == null || dbUser == null || dbPass == null) {
                throw new IllegalStateException("DATABASE_URL, DB_USER, and DB_PASS must be set");
            }
            connection = DriverManager.getConnection(jdbcUrl, dbUser, dbPass);
            log.info("✅ Connected to database: {}", jdbcUrl);
        } catch (Exception e) {
            log.error("❌ Failed to initialize database connection", e);
            System.exit(1);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Histogram of DB latency
    // ──────────────────────────────────────────────────────────────────────────
    private static final Summary dbLatency = Summary.build()
        .name("db_query_duration_seconds")
        .help("Database query latency in seconds")
        .register();

    /** Executes a SELECT and returns a list of rows, each as a Map. */
    public static List<Map<String, Object>> query(String sql, Object... params) {
        long start = System.nanoTime();
        log.info("QUERY → {}  params={}", sql, Arrays.toString(params));
        List<Map<String, Object>> results = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            ResultSet rs = stmt.executeQuery();
            ResultSetMetaData meta = rs.getMetaData();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    row.put(meta.getColumnName(i), rs.getObject(i));
                }
                results.add(row);
            }
        } catch (SQLException e) {
            log.error("SQL error on query", e);
        } finally {
            double secs = (System.nanoTime() - start) / 1e9;
            dbLatency.observe(secs);
            log.info("Query completed in {} ms", (int)(secs * 1000));
        }
        return results;
    }

    /** Executes an INSERT, UPDATE, or DELETE. */
    public static void update(String sql, Object... params) {
        long start = System.nanoTime();
        log.info("UPDATE → {}  params={}", sql, Arrays.toString(params));
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("SQL error on update", e);
        } finally {
            double secs = (System.nanoTime() - start) / 1e9;
            dbLatency.observe(secs);
            log.info("Update completed in {} ms", (int)(secs * 1000));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Data-access methods using singular table names
    // ──────────────────────────────────────────────────────────────────────────

    public static Map<String, Object> getUserByUsername(String username) {
        List<Map<String, Object>> rows = query(
            "SELECT * FROM `user` WHERE username = ?",
            username);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public static Map<String, Object> getUserById(int userId) {
        List<Map<String, Object>> rows = query(
            "SELECT * FROM `user` WHERE user_id = ?",
            userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public static void createUser(String username, String email, String pwHash) {
        update(
            "INSERT INTO `user` (username, email, pw_hash) VALUES (?, ?, ?)",
            username, email, pwHash);
    }

    public static List<Map<String, Object>> getTimelineMessages(int userId, int limit) {
        return query(
            "SELECT m.*, u.* " +
            "FROM `message` m " +
            "JOIN `user` u ON m.author_id = u.user_id " +
            "WHERE m.flagged = 0 " +
              "AND (u.user_id = ? OR u.user_id IN " +
                  "(SELECT whom_id FROM `follower` WHERE who_id = ?)) " +
            "ORDER BY m.pub_date DESC " +
            "LIMIT ?",
            userId, userId, limit);
    }

    public static List<Map<String, Object>> getPublicTimeline(int limit) {
        return query(
            "SELECT m.*, u.* " +
            "FROM `message` m " +
            "JOIN `user` u ON m.author_id = u.user_id " +
            "WHERE m.flagged = 0 " +
            "ORDER BY m.pub_date DESC " +
            "LIMIT ?",
            limit);
    }

    public static boolean isFollowing(int whoId, int whomId) {
        List<Map<String, Object>> rows = query(
            "SELECT 1 FROM `follower` WHERE who_id = ? AND whom_id = ?",
            whoId, whomId);
        return !rows.isEmpty();
    }

    public static void followUser(int whoId, int whomId) {
        update(
            "INSERT INTO `follower` (who_id, whom_id) VALUES (?, ?)",
            whoId, whomId);
    }

    public static void unfollowUser(int whoId, int whomId) {
        update(
            "DELETE FROM `follower` WHERE who_id = ? AND whom_id = ?",
            whoId, whomId);
    }

    public static void createMessage(int authorId, String text, long pubDate) {
        update(
            "INSERT INTO `message` (author_id, text, pub_date, flagged) VALUES (?, ?, ?, 0)",
            authorId, text, pubDate);
    }

    public static List<Map<String, Object>> getUserTimeline(int userId, int limit) {
        return query(
            "SELECT m.*, u.username, u.email " +
            "FROM `message` m " +
            "JOIN `user` u ON m.author_id = u.user_id " +
            "WHERE m.author_id = ? " +
            "ORDER BY m.pub_date DESC " +
            "LIMIT ?",
            userId, limit);
    }

    /** Returns the highest message_id (or 0 if none). */
    public static int getLatestCommandId() {
        List<Map<String, Object>> rows = query(
            "SELECT message_id FROM `message` ORDER BY message_id DESC LIMIT 1");
        if (rows.isEmpty()) return 0;
        Number n = (Number) rows.get(0).get("message_id");
        return n == null ? 0 : n.intValue();
    }
}
