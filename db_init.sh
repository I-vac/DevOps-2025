#!/bin/bash

DB_PATH="/data/minitwit.db"
if [ ! -f "$DB_PATH" ]; then
  echo "Initializing database..."
  sqlite3 "$DB_PATH" < /app/schema.sql
else
  echo "Database already initialized."
fi

# start the app WITH the JMX exporter
exec java \
  -javaagent:/app/jmx_prometheus_javaagent-0.18.0.jar=9404:/app/jmx_config.yml \
  -jar app.jar
