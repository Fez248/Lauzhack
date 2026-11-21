# ThisIsTea server image (plan §15). Build from the repo root:
#   docker compose -f deploy/docker-compose.yml build
# The JDK image + the Gradle wrapper keep the Gradle version identical to local builds.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :server:installDist --no-daemon --console=plain

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/server/build/install/server /app
# Items (for leaderboard XP) and field templates are read at runtime.
COPY content /app/content
ENV CONTENT_DIR=/app/content \
    PORT=8080
EXPOSE 8080
CMD ["/app/bin/server"]
