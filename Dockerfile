# syntax=docker/dockerfile:1

# ---- Build stage ----
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# `gradle build` runs jOOQ codegen (generateJooq), which needs a live
# Postgres connection to introspect the real schema before compiling -- see
# build.gradle.kts's loadEnv(). These three must be set as BUILD-TIME
# environment variables on the Render service (Settings > Environment,
# scoped to the build), not just runtime ones, or the Docker build fails.
ARG SPRING_DATASOURCE_URL
ARG SPRING_DATASOURCE_USERNAME
ARG SPRING_DATASOURCE_PASSWORD
ENV SPRING_DATASOURCE_URL=$SPRING_DATASOURCE_URL \
    SPRING_DATASOURCE_USERNAME=$SPRING_DATASOURCE_USERNAME \
    SPRING_DATASOURCE_PASSWORD=$SPRING_DATASOURCE_PASSWORD

# Wrapper + build scripts first so dependency resolution is cached in its
# own layer and only re-runs when these actually change.
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle gradle
RUN chmod +x ./gradlew
RUN ./gradlew --version

COPY src src

RUN ./gradlew clean build -x test --no-daemon

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

COPY --from=build /app/build/libs/cocoa.jar app.jar

EXPOSE 3001
ENTRYPOINT ["java", "-jar", "app.jar"]
