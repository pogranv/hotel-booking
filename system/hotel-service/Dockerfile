FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :hotel-service:bootJar --no-daemon -x test

FROM eclipse-temurin:17-jre
RUN if id -u 1000 >/dev/null 2>&1; then userdel "$(id -un 1000)"; fi && useradd --system --uid 1000 --create-home spring
WORKDIR /app
COPY --from=build --chown=spring:spring /workspace/hotel-service/build/libs/hotel-service-0.0.1-SNAPSHOT.jar app.jar
USER spring
EXPOSE 8082
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-jar", "/app/app.jar"]
