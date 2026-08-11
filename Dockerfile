# syntax=docker/dockerfile:1

# ---- Build stage ----
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# Wrapper + build scripts first so dependency resolution is cached in its
# own layer and only re-runs when these actually change.
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle gradle
RUN chmod +x ./gradlew
RUN ./gradlew --version

COPY src src

# `gradle build` runs jOOQ codegen (generateJooq), which needs a live
# Postgres connection to introspect the real schema before compiling -- see
# build.gradle.kts's loadEnv(). Render injects each service environment
# variable as a BuildKit secret of the same name (render.com/docs/docker-secrets),
# so these are read from /run/secrets/* instead of ARG/ENV -- ARG/ENV would
# bake the DB password into the image's layer history permanently.
RUN --mount=type=secret,id=SPRING_DATASOURCE_URL \
    --mount=type=secret,id=SPRING_DATASOURCE_USERNAME \
    --mount=type=secret,id=SPRING_DATASOURCE_PASSWORD \
    export SPRING_DATASOURCE_URL="$(cat /run/secrets/SPRING_DATASOURCE_URL)" && \
    export SPRING_DATASOURCE_USERNAME="$(cat /run/secrets/SPRING_DATASOURCE_USERNAME)" && \
    export SPRING_DATASOURCE_PASSWORD="$(cat /run/secrets/SPRING_DATASOURCE_PASSWORD)" && \
    ./gradlew clean build -x test --no-daemon

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

COPY --from=build /app/build/libs/cocoa.jar app.jar

EXPOSE 3001
ENTRYPOINT ["java", "-jar", "app.jar"]
