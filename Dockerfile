# Builder Stage
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Build the application, skipping tests for faster build
RUN mvn clean package -DskipTests

# Run Stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
# Copy the built jar from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Expose the application port (Hugging Face default is 7860)
EXPOSE 7860

# Run the application using the shell-form of ENTRYPOINT for environment variable expansion
ENTRYPOINT java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Xss512k -Dserver.port=${PORT:-7860} -jar app.jar
