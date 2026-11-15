FROM eclipse-temurin:21.0.11_10-jdk AS build
WORKDIR /workspace
COPY . .
ARG MODULE
RUN test -n "$MODULE" && ./gradlew --no-daemon ":${MODULE}:bootJar" && cp "${MODULE}/build/libs/app.jar" /app.jar

FROM eclipse-temurin:21.0.11_10-jre
WORKDIR /app
COPY --from=build /app.jar /app/app.jar
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
