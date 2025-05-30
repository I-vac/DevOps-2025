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

    // 1️⃣ Define & register a histogram for all DB operations
    private static final Summary dbLatency = Summary.build()
        .name("db_query_duration_seconds")
        .help("Database query latency in seconds")
        .register();

    public static void init() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            String dbPath = System.getenv("DATABASE_URL");
            if (dbPath == null) {
                dbPath = "jdbc:mysql://localhost:3306/minitwit_db?serverTimezone=UTC";
            }
            connection = DriverManager.getConnection(dbPath); 
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Executes a SELECT and returns a list of rows, each row as a Map. */
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
                    row.put(meta.getColumnLabel(i), rs.getObject(i));
                }
                results.add(row);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            double seconds = (System.nanoTime() - start) / 1e9;
            dbLatency.observe(seconds);
            log.info("Query completed in {} ms", (int)(seconds * 1000));
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
            e.printStackTrace();
        } finally {
            double seconds = (System.nanoTime() - start) / 1e9;
            dbLatency.observe(seconds);
            log.info("Update completed in {} ms", (int)(seconds * 1000));
        }
    }

    public static Map<String, Object> getUserByUsername(String username) {
        List<Map<String, Object>> users = query(
            "SELECT * FROM user WHERE username = ?", username);
        return users.isEmpty() ? null : users.get(0);
    }

    public static Map<String, Object> getUserById(int userId) {
        List<Map<String, Object>> users = query(
            "SELECT * FROM user WHERE user_id = ?", userId);
        return users.isEmpty() ? null : users.get(0);
    }

    public static void createUser(String username, String email, String pwHash) {
        update(
            "INSERT INTO user (username, email, pw_hash) VALUES (?, ?, ?)",
            username, email, pwHash);
    }

    public static List<Map<String, Object>> getTimelineMessages(int userId, int limit) {
        return query("""
            SELECT 
                message.text AS text,
                message.pub_date AS pub_date,
                user.username AS username,
                user.email AS email
            FROM message
            JOIN user ON message.author_id = user.user_id
            WHERE message.flagged = 0
              AND (
                user.user_id = ? 
                OR user.user_id IN (SELECT whom_id FROM follower WHERE who_id = ?)
              )
            ORDER BY message.pub_date DESC 
            LIMIT ?""",
            userId, userId, limit);
    }
    

    public static List<Map<String, Object>> getPublicTimeline(int limit) {
        return query("""
            SELECT 
                message.text AS text,
                message.pub_date AS pub_date,
                user.username AS username,
                user.email AS email
            FROM message
            JOIN user ON message.author_id = user.user_id
            WHERE message.flagged = 0
            ORDER BY message.pub_date DESC
            LIMIT ?""",
            limit);
    }

    public static boolean isFollowing(int whoId, int whomId) {
        List<Map<String, Object>> rows = query(
            "SELECT 1 FROM follower WHERE who_id = ? AND whom_id = ?",
            whoId, whomId);
        return !rows.isEmpty();
    }

    public static void followUser(int whoId, int whomId) {
        update(
            "INSERT INTO follower (who_id, whom_id) VALUES (?, ?)",
            whoId, whomId);
    }

    public static void unfollowUser(int whoId, int whomId) {
        update(
            "DELETE FROM follower WHERE who_id = ? AND whom_id = ?",
            whoId, whomId);
    }

    public static void createMessage(int authorId, String text, long pubDate) {
        update(
            "INSERT INTO message (author_id, text, pub_date, flagged) VALUES (?, ?, ?, 0)",
            authorId, text, pubDate);
    }

    public static List<Map<String, Object>> getUserTimeline(int userId, int limit) {
        return query("""
            SELECT 
                message.text AS text,
                message.pub_date AS pub_date,
                user.username AS username,
                user.email AS email
            FROM message 
            JOIN user ON message.author_id = user.user_id 
            WHERE message.author_id = ? 
            ORDER BY message.pub_date DESC 
            LIMIT ?""",
            userId, limit);
    }

    /** Returns the highest message_id in the table (or 0 if empty). */
    public static int getLatestCommandId() {
        List<Map<String, Object>> rows = query(
            "SELECT message_id FROM message ORDER BY message_id DESC LIMIT 1");
        if (rows.isEmpty()) {
            return 0;
        }
        Number n = (Number) rows.get(0).get("message_id");
        return (n == null ? 0 : n.intValue());
    }
}
