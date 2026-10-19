# syntax=docker/dockerfile:1.7
# One Dockerfile for every service:  docker build --build-arg MODULE=services/campaign-service -t nba/campaign-service .
FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl ${MODULE} -am package -DskipTests && \
    cp $(ls ${MODULE}/target/*.jar | grep -v plain) /app.jar && \
    mkdir /layers && cd /layers && java -Djarmode=tools -jar /app.jar extract --layers --launcher --destination .

FROM eclipse-temurin:21-jre-noble
WORKDIR /app
# Group 0 with group permissions so OpenShift's arbitrary UIDs (always in group 0) can run the image.
COPY --from=build --chown=10001:0 /layers/dependencies/ ./
COPY --from=build --chown=10001:0 /layers/spring-boot-loader/ ./
COPY --from=build --chown=10001:0 /layers/snapshot-dependencies/ ./
COPY --from=build --chown=10001:0 /layers/application/ ./
RUN chmod -R g=u /app
USER 10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.io.tmpdir=/tmp"
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
