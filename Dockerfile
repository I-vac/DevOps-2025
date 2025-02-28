# Build Stage
FROM jelastic/maven:3.9.5-openjdk-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package

# Runtime Stage
FROM openjdk:21-slim
WORKDIR /app

COPY --from=build /app/target/minitwit-java-app.jar app.jar

EXPOSE 5000
CMD ["java", "-jar", "app.jar"]
