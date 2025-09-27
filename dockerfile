FROM openjdk:25-jdk-slim

# Set working directory
WORKDIR /app

# Copy built jar into container
COPY target/memora-db-1.0.0.jar memora-db.jar

# Expose your port
EXPOSE 9090

# Run the jar
ENTRYPOINT ["java", "-jar", "memora-db.jar"]

