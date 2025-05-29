# Build stage
FROM jelastic/maven:3.9.5-openjdk-21 AS build
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn clean package

# Runtime stage
FROM eclipse-temurin:21-jdk
WORKDIR /app

# Application ports
EXPOSE 5000
EXPOSE 9404
EXPOSE 9091

# JMX exporter
COPY monitoring/jmx_prometheus_javaagent-0.18.0.jar /app/jmx_prometheus_javaagent-0.18.0.jar
COPY monitoring/jmx_config.yml            /app/jmx_config.yml

# Application JAR
COPY --from=build /app/target/minitwit-java-app.jar /app/app.jar

# Launch the application with JMX exporter
ENTRYPOINT ["java", "-javaagent:/app/jmx_prometheus_javaagent-0.18.0.jar=9404:/app/jmx_config.yml", "-jar", "/app/app.jar"]
