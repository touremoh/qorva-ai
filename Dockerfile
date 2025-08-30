# ---- Build stage ----
FROM maven:3.9.9-amazoncorretto-23-debian AS build
WORKDIR ~/workspace/qorva/qorva-ai
COPY pom.xml .
RUN mvn -q -e -DskipTests dependency:go-offline
COPY src ./src
# Build Spring Boot fat jar
RUN mvn -q -DskipTests package

# ---- Run stage ----
FROM amazoncorretto:23.0.0-alpine3.20
WORKDIR /app
# Copy the built jar
COPY --from=build ~/workspace/qorva/qorva-ai/target/qorva-ai-1.0.1.jar app.jar
# Spring Boot defaults to 8080; App Runner's default container port is 8080
EXPOSE 8080
ENTRYPOINT ["java","-XX:+UseZGC","-jar","/app/app.jar"]
