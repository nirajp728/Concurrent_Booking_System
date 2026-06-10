# Stage 1: Build the artifact and cache dependencies
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy ONLY the pom.xml first to cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Now copy the source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Execute the thin JRE binary
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]