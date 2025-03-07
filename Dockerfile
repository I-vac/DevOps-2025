# Build Stage
FROM jelastic/maven:3.9.5-openjdk-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package

# Runtime Stage
FROM openjdk:21-slim
WORKDIR /app
RUN apt-get update && apt-get install -y sqlite3 && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/minitwit-java-app.jar app.jar
COPY src/main/resources/schema.sql /app/schema.sql
COPY db_init.sh /app/db_init.sh
RUN chmod +x /app/db_init.sh

EXPOSE 5000
CMD ["/app/db_init.sh"]