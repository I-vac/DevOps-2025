# Build
FROM jelastic/maven:3.9.5-openjdk-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package

# Runtime
FROM eclipse-temurin:21-jdk
WORKDIR /app

# JMX exporter
# copy the JMX exporter and config so the script can pick them up
COPY monitoring/jmx_prometheus_javaagent-0.18.0.jar /app/jmx_prometheus_javaagent-0.18.0.jar
COPY monitoring/jmx_config.yml            /app/jmx_config.yml
EXPOSE 9404

# App
COPY --from=build /app/target/minitwit-java-app.jar app.jar

# Ports
EXPOSE 5000
CMD ["java", "-jar", "app.jar"]
