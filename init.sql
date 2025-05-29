-- init.sql

-- 1) Create the database
CREATE DATABASE IF NOT EXISTS minitwit_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 2) Create (or reset) your app user with remote host access
CREATE USER IF NOT EXISTS 'minitwit'@'%' IDENTIFIED BY 'minitwitpass';
GRANT ALL PRIVILEGES ON minitwit_db.* TO 'minitwit'@'%';
FLUSH PRIVILEGES;

-- 3) Switch into your database
USE minitwit_db;

-- 4) Drop & recreate your tables
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  user_id    INT AUTO_INCREMENT PRIMARY KEY,
  username   VARCHAR(255) NOT NULL UNIQUE,
  email      VARCHAR(255) NOT NULL UNIQUE,
  pw_hash    VARCHAR(255) NOT NULL
);

DROP TABLE IF EXISTS `follower`;
CREATE TABLE `follower` (
  who_id   INT,
  whom_id  INT,
  FOREIGN KEY (who_id)  REFERENCES `user`(user_id) ON DELETE CASCADE,
  FOREIGN KEY (whom_id) REFERENCES `user`(user_id) ON DELETE CASCADE
);

DROP TABLE IF EXISTS `message`;
CREATE TABLE `message` (
  message_id INT AUTO_INCREMENT PRIMARY KEY,
  author_id  INT NOT NULL,
  text       TEXT NOT NULL,
  pub_date   INT,
  flagged    TINYINT(1) DEFAULT 0,
  FOREIGN KEY (author_id) REFERENCES `user`(user_id) ON DELETE CASCADE
);
