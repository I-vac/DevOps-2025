# Build
FROM jelastic/maven:3.9.5-openjdk-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package

# Runtime
FROM eclipse-temurin:21-jdk
WORKDIR /app
RUN apt-get update \
 && apt-get install -y --no-install-recommends sqlite3 \
 && rm -rf /var/lib/apt/lists/*

# JMX exporter
# copy the JMX exporter and config so the script can pick them up
COPY monitoring/jmx_prometheus_javaagent-0.18.0.jar /app/jmx_prometheus_javaagent-0.18.0.jar
COPY monitoring/jmx_config.yml            /app/jmx_config.yml
EXPOSE 9404

# App
COPY --from=build /app/target/minitwit-java-app.jar app.jar
COPY src/main/resources/schema.sql /app/schema.sql
COPY db_init.sh                   /app/db_init.sh
RUN chmod +x /app/db_init.sh

# Ports
EXPOSE 5000
CMD ["/app/db_init.sh"]
