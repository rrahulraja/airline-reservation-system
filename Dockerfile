# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
# Flyway reads migrations from filesystem:db/migrations relative to the working
# directory, so the migration scripts ship alongside the jar.
COPY db/migrations ./db/migrations
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
