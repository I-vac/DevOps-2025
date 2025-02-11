package minitwit;

import java.sql.*;
import java.util.*;

public class Database {
    private static Connection connection;
    public static final int PER_PAGE = 30;

    public static void init() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:minitwit.db");
            connection.createStatement().execute("PRAGMA foreign_keys = ON");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static List<Map<String, Object>> query(String sql, Object... params) {
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
            e.printStackTrace();
        }
        return results;
    }

    public static void update(String sql, Object... params) {
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static Map<String, Object> getUserByUsername(String username) {
        List<Map<String, Object>> users = query("SELECT * FROM user WHERE username = ?", username);
        return users.isEmpty() ? null : users.get(0);
    }

    public static Map<String, Object> getUserById(int userId) {
        List<Map<String, Object>> users = query("SELECT * FROM user WHERE user_id = ?", userId);
        return users.isEmpty() ? null : users.get(0);
    }

    public static void createUser(String username, String email, String pwHash) {
        update("INSERT INTO user (username, email, pw_hash) VALUES (?, ?, ?)", 
             username, email, pwHash);
    }

    public static List<Map<String, Object>> getTimelineMessages(int userId, int limit) {
        return query("""
            SELECT message.*, user.* FROM message, user
            WHERE message.flagged = 0 AND message.author_id = user.user_id AND (
                user.user_id = ? OR 
                user.user_id IN (SELECT whom_id FROM follower WHERE who_id = ?))
            ORDER BY message.pub_date DESC LIMIT ?""",
            userId, userId, limit);
    }

    public static List<Map<String, Object>> getPublicTimeline(int limit) {
        return query("""
            SELECT message.*, user.* FROM message, user
            WHERE message.flagged = 0 AND message.author_id = user.user_id
            ORDER BY message.pub_date DESC LIMIT ?""", 
            limit);
    }

    public static boolean isFollowing(int whoId, int whomId) {
        List<Map<String, Object>> result = query(
            "SELECT 1 FROM follower WHERE who_id = ? AND whom_id = ?", 
            whoId, whomId
        );
        return !result.isEmpty();
    }

    public static void followUser(int whoId, int whomId) {
        update("INSERT INTO follower (who_id, whom_id) VALUES (?, ?)", whoId, whomId);
    }

    public static void unfollowUser(int whoId, int whomId) {
        update("DELETE FROM follower WHERE who_id = ? AND whom_id = ?", whoId, whomId);
    }

    public static void createMessage(int authorId, String text, long pubDate) {
        update("INSERT INTO message (author_id, text, pub_date, flagged) VALUES (?, ?, ?, 0)",
              authorId, text, pubDate);
    }

    public static List<Map<String, Object>> getUserTimeline(int userId, int limit) {
        return query(""" 
            SELECT message.*, user.username, user.email 
            FROM message 
            JOIN user ON message.author_id = user.user_id 
            WHERE message.author_id = ? 
            ORDER BY message.pub_date DESC 
            LIMIT ?""", userId, limit);
    }
}