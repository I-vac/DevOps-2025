#!/bin/bash

# Set database path
DB_PATH="/data/minitwit.db"

# Check if database exists
if [ ! -f "$DB_PATH" ]; then
  echo "Initializing database..."
  sqlite3 "$DB_PATH" < /app/schema.sql
else
  echo "Database already initialized."
fi

# Start the application
exec java -jar app.jar