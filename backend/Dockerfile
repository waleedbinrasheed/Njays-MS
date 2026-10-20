# Spring Boot API. Built inside Docker, so the build machine needs no Java or Maven.
#   docker build -t leadingims/api:latest backend

# ---- build ---------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

# Dependencies first, so they stay cached when only the source changes.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q -DskipTests package && cp target/inventory-backend-*.jar /src/app.jar

# ---- run -----------------------------------------------------------------
FROM eclipse-temurin:21-jre

# curl is only used by the container health check.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 --home-dir /app ims

WORKDIR /app
COPY --from=build /src/app.jar /app/app.jar
USER ims

# Inside Docker the API listens on all container interfaces; docker-compose does not publish it,
# so it is still reachable only from the web container.
ENV SERVER_ADDRESS=0.0.0.0 \
    SERVER_PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8080/api/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
