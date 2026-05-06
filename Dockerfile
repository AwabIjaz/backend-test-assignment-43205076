# Stage 1: build
# Candidates do not need Java or Gradle installed locally.
# The entire build runs inside this container.
FROM gradle:8-jdk21 AS build
WORKDIR /app

# Copy build configuration first so dependency resolution can be cached.
COPY gradlew gradlew.bat build.gradle settings.gradle gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies

# Copy source code last to avoid invalidating dependency cache on every code change.
COPY src ./src
RUN ./gradlew build -x test --no-daemon

# Stage 2: run
FROM registry.access.redhat.com/ubi9/openjdk-21:1.23
ENV LANGUAGE='en_US:en'

COPY --chown=185 --from=build /app/build/quarkus-app/lib/      /deployments/lib/
COPY --chown=185 --from=build /app/build/quarkus-app/*.jar     /deployments/
COPY --chown=185 --from=build /app/build/quarkus-app/app/      /deployments/app/
COPY --chown=185 --from=build /app/build/quarkus-app/quarkus/  /deployments/quarkus/

EXPOSE 8080
USER 185
