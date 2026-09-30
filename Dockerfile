# ---- build: compile and run the unit tests ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package

# ---- run: small JRE image, non-root user ----
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --no-create-home agrishare \
    && mkdir -p /data/uploads && chown agrishare /data/uploads
COPY --from=build /app/target/agrishare-*.jar app.jar
USER agrishare
# photos go to /data/uploads; mount a persistent disk there or they vanish on redeploy
ENV APP_UPLOAD_DIR=/data/uploads \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
