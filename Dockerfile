# ============================================================
# Stage 1: Builder
# ============================================================
FROM maven:3.8.6-openjdk-8-slim AS builder

WORKDIR /workspace

# Copy dependency descriptor first for layer caching
COPY pom.xml .

# Download all dependencies (cached layer unless pom.xml changes)
RUN mvn dependency:go-offline -B

# Copy full source tree
COPY src ./src

# Build the application JAR (skip tests for Docker build)
RUN mvn clean package -DskipTests -B

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM eclipse-temurin:8-jdk

# Timezone configuration
ENV TZ=UTC

# Create non-root user for security
RUN groupadd --system appgroup && useradd --system --gid appgroup --shell /bin/false appuser

WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /workspace/target/*.jar app.jar

# Set ownership
RUN chown -R appuser:appgroup /app

USER appuser

# Application port (externalized via SERVER_PORT env var)
EXPOSE 8080

# JVM optimizations for containerized environments
ENV JAVA_OPTS="-Xms256m -Xmx512m \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -Djava.security.egd=file:/dev/./urandom \
  -Dfile.encoding=UTF-8 \
  -Duser.timezone=UTC"

# Spring Boot profile and port
ENV SPRING_PROFILES_ACTIVE=docker
ENV SERVER_PORT=8080

# Redis defaults (override via env vars in Kubernetes)
ENV REDIS_HOST=localhost
ENV REDIS_PORT=6379
ENV REDIS_PASSWORD=

# Azure Blob Storage defaults (override via env vars in Kubernetes)
ENV AZURE_BLOB_REPORTS_URL=https://storageaccount.blob.core.windows.net/reports
ENV AZURE_BLOB_BACKUP_URL=https://storageaccount.blob.core.windows.net/backups

# Payment API default (override via env vars in Kubernetes)
ENV PAYMENT_API_URL=http://payment-service/payments/charge

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
