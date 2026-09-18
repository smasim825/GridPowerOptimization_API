# Stage 1: Build the Maven application
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml and dependencies first for optimal docker caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build production jar
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Create lightweight runtime container
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Expose HTTP port (default 8080)
ENV PORT=8080
EXPOSE 8080

# Copy executable jar from build stage
COPY --from=build /app/target/gridwise-llm-service-1.0.0.jar app.jar

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]
