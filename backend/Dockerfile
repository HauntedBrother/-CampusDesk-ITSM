FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# Resolve application dependencies without fetching unused deployment/database plugins.
RUN mvn -B dependency:resolve
COPY src src
# Unit/HTTP tests run in the image build. The complete suite, including
# Testcontainers, runs on the Docker-enabled host (BuildKit has no engine socket).
RUN mvn -B package '-Dtest=*Test,!*IntegrationTest'
FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /app/target/itsm-0.1.0.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
