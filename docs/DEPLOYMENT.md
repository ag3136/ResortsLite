# ResortsLite - Deployment Guide for GCP GKE

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Local Development Setup](#local-development-setup)
4. [Building and Pushing Docker Images](#building-and-pushing-docker-images)
5. [GCP GKE Deployment](#gcp-gke-deployment)
6. [Configuration Management](#configuration-management)
7. [Monitoring and Troubleshooting](#monitoring-and-troubleshooting)
8. [Scaling and Management](#scaling-and-management)
9. [Security Considerations](#security-considerations)
10. [Technology-Specific Notes](#technology-specific-notes)

---

## Overview

ResortsLite is a Spring Boot 2.7.x application built with Java 8, designed for containerized deployment on Google Kubernetes Engine (GKE). This guide provides comprehensive instructions for building, deploying, and managing the application in a cloud-native environment.

**Technology Stack:**
- Java 8
- Spring Boot 2.7.18
- Maven 3.8.6+
- Docker
- Kubernetes (GKE)
- Redis (for session management and caching)
- H2 Database (in-memory for development)

---

## Prerequisites

### Required Tools

1. **Java Development Kit (JDK) 8**
   ```bash
   java -version
   # Should show Java 1.8.x
   ```

2. **Maven 3.8.6 or higher**
   ```bash
   mvn -version
   ```

3. **Docker Desktop or Docker Engine**
   ```bash
   docker --version
   # Minimum version: 20.10.x
   ```

4. **Google Cloud SDK (gcloud CLI)**
   ```bash
   gcloud --version
   # Install from: https://cloud.google.com/sdk/docs/install
   ```

5. **kubectl (Kubernetes CLI)**
   ```bash
   kubectl version --client
   # Install from: https://kubernetes.io/docs/tasks/tools/
   ```

### GCP Requirements

1. **GCP Project**: Active Google Cloud Platform project with billing enabled
2. **GKE Cluster**: A running GKE cluster (or permissions to create one)
3. **Artifact Registry**: Repository for storing Docker images
4. **IAM Permissions**: 
   - Kubernetes Engine Admin
   - Artifact Registry Writer
   - Service Account User

### External Services

1. **Redis Instance**: Google Cloud Memorystore for Redis or external Redis server
   - Required for session management and distributed caching
   - Minimum version: Redis 5.0+

---

## Local Development Setup

### 1. Clone and Build the Application

```bash
# Navigate to project directory
cd /path/to/RLMono

# Build the application with Maven
mvn clean package -DskipTests

# Verify the JAR file is created
ls -lh target/*.jar
```

### 2. Run Locally with Docker Compose

```bash
# Build and start the application
docker-compose up --build

# Access the application
# Application: http://localhost:8080
# H2 Console: http://localhost:8080/h2-console
# Health Check: http://localhost:8080/actuator/health
```

### 3. Environment Variables for Local Development

Create a `.env` file in the project root:

```env
# Redis Configuration
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# Application Configuration
INVENTORY_SERVICE_URL=http://localhost:8080
CACHE_TTL=3600

# JVM Options
JAVA_OPTS=-Xmx512m -Xms256m
```

### 4. Stop the Application

```bash
docker-compose down
```

---

## Building and Pushing Docker Images

### Option 1: Using build-push.sh (Linux/macOS)

```bash
# Make the script executable
chmod +x scripts/build-push.sh

# Run the build and push script
./scripts/build-push.sh
```

**Script Workflow:**
1. Prompts for image tag (default: latest)
2. Select registry type:
   - Google Artifact Registry (recommended for GKE)
   - Docker Hub
3. Enter registry credentials
4. Builds Docker image
5. Pushes to selected registry

### Option 2: Using build-push.bat (Windows)

```cmd
# Run the build and push script
scripts\build-push.bat
```

### Option 3: Manual Build and Push

#### For Google Artifact Registry:

```bash
# Set variables
export GCP_PROJECT="your-gcp-project-id"
export GCP_REGION="us-central1"
export ARTIFACT_REPO="resortslite-repo"
export IMAGE_TAG="v1.0.0"

# Authenticate with GCP
gcloud auth login
gcloud config set project $GCP_PROJECT

# Configure Docker for Artifact Registry
gcloud auth configure-docker ${GCP_REGION}-docker.pkg.dev

# Build the image
docker build -t ${GCP_REGION}-docker.pkg.dev/${GCP_PROJECT}/${ARTIFACT_REPO}/resortslite:${IMAGE_TAG} .

# Push the image
docker push ${GCP_REGION}-docker.pkg.dev/${GCP_PROJECT}/${ARTIFACT_REPO}/resortslite:${IMAGE_TAG}
```

#### For Docker Hub:

```bash
# Set variables
export DOCKER_USERNAME="your-dockerhub-username"
export IMAGE_TAG="v1.0.0"

# Login to Docker Hub
docker login

# Build the image
docker build -t ${DOCKER_USERNAME}/resortslite:${IMAGE_TAG} .

# Push the image
docker push ${DOCKER_USERNAME}/resortslite:${IMAGE_TAG}
```

---

## GCP GKE Deployment

### Step 1: Create GKE Cluster (if not exists)

```bash
# Set variables
export GCP_PROJECT="your-gcp-project-id"
export GCP_ZONE="us-central1-a"
export CLUSTER_NAME="resortslite-cluster"

# Create GKE cluster
gcloud container clusters create $CLUSTER_NAME \
  --zone $GCP_ZONE \
  --project $GCP_PROJECT \
  --num-nodes 3 \
  --machine-type n1-standard-2 \
  --enable-autoscaling \
  --min-nodes 2 \
  --max-nodes 5 \
  --enable-autorepair \
  --enable-autoupgrade
```

### Step 2: Configure kubectl

```bash
# Get cluster credentials
gcloud container clusters get-credentials $CLUSTER_NAME \
  --zone $GCP_ZONE \
  --project $GCP_PROJECT

# Verify connection
kubectl cluster-info
kubectl get nodes
```

### Step 3: Deploy Using Automated Script

#### Linux/macOS:

```bash
# Make the script executable
chmod +x scripts/deploy-image.sh

# Run the deployment script
./scripts/deploy-image.sh
```

#### Windows:

```cmd
# Run the deployment script
scripts\deploy-image.bat
```

**Script Prompts:**
1. GCP Project ID
2. GCP Zone
3. GKE Cluster Name
4. Docker Image URI (full path with tag)
5. Redis Host (e.g., 10.0.0.3 for Memorystore)
6. Redis Port (default: 6379)
7. Redis Password (if authentication enabled)
8. Inventory Service URL (for microservices)

### Step 4: Manual Deployment (Alternative)

```bash
# Update deployment.yaml with your image URI
export IMAGE_URI="us-central1-docker.pkg.dev/your-project/resortslite-repo/resortslite:v1.0.0"

# Update manifests
sed -i "s|{{IMAGE_URI}}|$IMAGE_URI|g" kubernetes/deployment.yaml
sed -i "s|{{REDIS_HOST}}|10.0.0.3|g" kubernetes/deployment.yaml
sed -i "s|{{REDIS_PORT}}|6379|g" kubernetes/deployment.yaml
sed -i "s|{{REDIS_PASSWORD}}||g" kubernetes/deployment.yaml
sed -i "s|{{INVENTORY_SERVICE_URL}}|http://localhost:8080|g" kubernetes/deployment.yaml

# Apply manifests
kubectl apply -f kubernetes/namespace.yaml
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

# Wait for rollout
kubectl rollout status deployment/resortslite -n resortslite

# Verify deployment
kubectl get pods,svc,ingress -n resortslite
```

### Step 5: Access the Application

```bash
# Get the ingress IP address
kubectl get ingress resortslite-ingress -n resortslite

# Access the application
# http://<INGRESS_IP>/
```

---

## Configuration Management

### Environment Variables

The application uses the following environment variables:

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `SPRING_PROFILES_ACTIVE` | Spring Boot profile | docker | No |
| `SERVER_PORT` | Application port | 8080 | No |
| `REDIS_HOST` | Redis server hostname | localhost | Yes |
| `REDIS_PORT` | Redis server port | 6379 | No |
| `REDIS_PASSWORD` | Redis authentication password | (empty) | No |
| `INVENTORY_SERVICE_URL` | Inventory service endpoint | http://localhost:8080 | Yes |
| `REPORT_PATH` | Report output directory | /app/reports | No |
| `BACKUP_PATH` | Backup directory | /app/backups | No |
| `CACHE_TTL` | Cache time-to-live (seconds) | 3600 | No |
| `JAVA_OPTS` | JVM options | -Xmx512m -Xms256m | No |

### Kubernetes ConfigMaps

Create a ConfigMap for application configuration:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: resortslite-config
  namespace: resortslite
data:
  application.properties: |
    spring.application.name=ResortsLite
    server.port=8080
    app.cache.ttl=3600
```

Apply the ConfigMap:

```bash
kubectl apply -f configmap.yaml
```

### Kubernetes Secrets

Store sensitive data in Secrets:

```bash
# Create secret for Redis password
kubectl create secret generic redis-credentials \
  --from-literal=password='your-redis-password' \
  -n resortslite

# Update deployment to use secret
# Add to deployment.yaml under env:
# - name: REDIS_PASSWORD
#   valueFrom:
#     secretKeyRef:
#       name: redis-credentials
#       key: password
```

---

## Monitoring and Troubleshooting

### Health Checks

The application exposes Spring Boot Actuator endpoints:

- **Liveness Probe**: `/actuator/health/liveness`
- **Readiness Probe**: `/actuator/health/readiness`
- **General Health**: `/actuator/health`

Test health endpoints:

```bash
# Port-forward to access locally
kubectl port-forward -n resortslite deployment/resortslite 8080:8080

# Check health
curl http://localhost:8080/actuator/health
```

### View Logs

```bash
# View logs for all pods
kubectl logs -n resortslite -l app=resortslite

# Follow logs in real-time
kubectl logs -n resortslite -l app=resortslite -f

# View logs for specific pod
kubectl logs -n resortslite <pod-name>

# View previous container logs (if crashed)
kubectl logs -n resortslite <pod-name> --previous
```

### Common Issues and Solutions

#### 1. Pods Not Starting (ImagePullBackOff)

**Symptom**: Pods stuck in `ImagePullBackOff` state

**Solution**:
```bash
# Check pod events
kubectl describe pod -n resortslite <pod-name>

# Verify image URI is correct
kubectl get deployment resortslite -n resortslite -o yaml | grep image:

# Ensure Artifact Registry authentication is configured
gcloud auth configure-docker us-central1-docker.pkg.dev
```

#### 2. Application Crashes (CrashLoopBackOff)

**Symptom**: Pods repeatedly crashing

**Solution**:
```bash
# Check logs for errors
kubectl logs -n resortslite <pod-name>

# Check resource limits
kubectl describe pod -n resortslite <pod-name> | grep -A 5 Limits

# Increase memory if OOMKilled
# Edit deployment.yaml and increase memory limits
```

#### 3. Redis Connection Failures

**Symptom**: Application logs show Redis connection errors

**Solution**:
```bash
# Verify Redis host and port
kubectl get deployment resortslite -n resortslite -o yaml | grep REDIS

# Test Redis connectivity from pod
kubectl exec -it -n resortslite <pod-name> -- sh
# (inside pod) telnet $REDIS_HOST $REDIS_PORT

# Check Redis Memorystore instance status in GCP Console
```

#### 4. Service Not Accessible

**Symptom**: Cannot access application via ingress

**Solution**:
```bash
# Check ingress status
kubectl get ingress -n resortslite
kubectl describe ingress resortslite-ingress -n resortslite

# Verify service endpoints
kubectl get endpoints -n resortslite

# Check if pods are ready
kubectl get pods -n resortslite

# Test service internally
kubectl run -it --rm debug --image=busybox --restart=Never -n resortslite -- wget -O- http://resortslite-service
```

### Debugging Commands

```bash
# Get detailed pod information
kubectl describe pod -n resortslite <pod-name>

# Execute commands inside pod
kubectl exec -it -n resortslite <pod-name> -- /bin/sh

# Check resource usage
kubectl top pods -n resortslite
kubectl top nodes

# View events
kubectl get events -n resortslite --sort-by='.lastTimestamp'
```

---

## Scaling and Management

### Manual Scaling

```bash
# Scale to 5 replicas
kubectl scale deployment/resortslite -n resortslite --replicas=5

# Verify scaling
kubectl get pods -n resortslite
```

### Horizontal Pod Autoscaler (HPA)

Create an HPA for automatic scaling:

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: resortslite-hpa
  namespace: resortslite
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: resortslite
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

Apply the HPA:

```bash
kubectl apply -f hpa.yaml

# Check HPA status
kubectl get hpa -n resortslite
```

### Rolling Updates

```bash
# Update image to new version
kubectl set image deployment/resortslite \
  resortslite=us-central1-docker.pkg.dev/project/repo/resortslite:v2.0.0 \
  -n resortslite

# Monitor rollout
kubectl rollout status deployment/resortslite -n resortslite

# Check rollout history
kubectl rollout history deployment/resortslite -n resortslite
```

### Rollback

```bash
# Rollback to previous version
kubectl rollout undo deployment/resortslite -n resortslite

# Rollback to specific revision
kubectl rollout undo deployment/resortslite -n resortslite --to-revision=2

# Verify rollback
kubectl rollout status deployment/resortslite -n resortslite
```

---

## Security Considerations

### 1. Container Security

- **Non-root User**: Application runs as non-root user `appuser`
- **Read-only Root Filesystem**: Consider adding `readOnlyRootFilesystem: true`
- **Security Context**: Add security context to deployment:

```yaml
securityContext:
  runAsNonRoot: true
  runAsUser: 1000
  capabilities:
    drop:
      - ALL
  allowPrivilegeEscalation: false
```

### 2. Network Policies

Implement network policies to restrict traffic:

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: resortslite-netpol
  namespace: resortslite
spec:
  podSelector:
    matchLabels:
      app: resortslite
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: ingress-nginx
    ports:
    - protocol: TCP
      port: 8080
  egress:
  - to:
    - namespaceSelector: {}
    ports:
    - protocol: TCP
      port: 6379  # Redis
```

### 3. Secrets Management

- Use Google Secret Manager for sensitive data
- Never commit secrets to version control
- Rotate credentials regularly
- Use Workload Identity for GCP service authentication

### 4. Image Security

```bash
# Scan images for vulnerabilities
gcloud container images scan us-central1-docker.pkg.dev/project/repo/resortslite:v1.0.0

# View scan results
gcloud container images list-tags us-central1-docker.pkg.dev/project/repo/resortslite --show-occurrences
```

### 5. RBAC (Role-Based Access Control)

Create service account with minimal permissions:

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: resortslite-sa
  namespace: resortslite
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: resortslite-role
  namespace: resortslite
rules:
- apiGroups: [""]
  resources: ["configmaps", "secrets"]
  verbs: ["get", "list"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: resortslite-rolebinding
  namespace: resortslite
subjects:
- kind: ServiceAccount
  name: resortslite-sa
roleRef:
  kind: Role
  name: resortslite-role
  apiGroup: rbac.authorization.k8s.io
```

---

## Technology-Specific Notes

### Java 8 and Spring Boot 2.7.x

1. **JVM Memory Management**:
   - Container-aware JVM flags are enabled: `-XX:+UseContainerSupport`
   - MaxRAMPercentage set to 75% to prevent OOM kills
   - Heap size: 256MB initial, 512MB maximum

2. **Spring Boot Actuator**:
   - Health endpoints enabled for Kubernetes probes
   - Liveness: `/actuator/health/liveness`
   - Readiness: `/actuator/health/readiness`

3. **Session Management**:
   - Spring Session with Redis for distributed sessions
   - Required for horizontal scaling
   - Configure Redis connection via environment variables

4. **Startup Time**:
   - Java 8 applications have longer startup times
   - Initial delay for liveness probe: 60 seconds
   - Initial delay for readiness probe: 30 seconds

5. **Graceful Shutdown**:
   - Spring Boot handles SIGTERM for graceful shutdown
   - Default timeout: 30 seconds
   - Configure with: `spring.lifecycle.timeout-per-shutdown-phase`

### Maven Build System

1. **Dependency Caching**:
   - Dockerfile copies `pom.xml` first
   - Downloads dependencies before copying source code
   - Improves build performance with layer caching

2. **Build Command**:
   - Uses system Maven: `mvn clean package -DskipTests`
   - Never uses Maven wrapper (mvnw) in Docker
   - Skips tests for faster builds

3. **Artifact Location**:
   - JAR file: `target/*.jar`
   - Copied to `/app/app.jar` in container

### Redis Integration

1. **Connection Configuration**:
   ```properties
   spring.redis.host=${REDIS_HOST}
   spring.redis.port=${REDIS_PORT}
   spring.redis.password=${REDIS_PASSWORD}
   ```

2. **Google Cloud Memorystore**:
   - Use private IP address for REDIS_HOST
   - Ensure GKE cluster is in same VPC
   - Enable VPC peering if needed

3. **Session Storage**:
   ```properties
   spring.session.store-type=redis
   ```

4. **Cache Configuration**:
   - TTL: 3600 seconds (configurable via CACHE_TTL)
   - Serialization: Jackson JSON

---

## Additional Resources

### Documentation Links

- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/2.7.x/reference/html/)
- [Google Kubernetes Engine](https://cloud.google.com/kubernetes-engine/docs)
- [Kubernetes Documentation](https://kubernetes.io/docs/home/)
- [Docker Documentation](https://docs.docker.com/)
- [Google Cloud Memorystore](https://cloud.google.com/memorystore/docs/redis)

### Useful Commands Reference

```bash
# GKE Cluster Management
gcloud container clusters list
gcloud container clusters describe $CLUSTER_NAME --zone $GCP_ZONE

# Kubernetes Operations
kubectl get all -n resortslite
kubectl describe deployment resortslite -n resortslite
kubectl get events -n resortslite --sort-by='.lastTimestamp'

# Logs and Debugging
kubectl logs -n resortslite -l app=resortslite --tail=100
kubectl exec -it -n resortslite <pod-name> -- /bin/sh

# Scaling and Updates
kubectl scale deployment/resortslite -n resortslite --replicas=3
kubectl rollout restart deployment/resortslite -n resortslite
kubectl rollout undo deployment/resortslite -n resortslite

# Cleanup
kubectl delete namespace resortslite
```

---

## Support and Maintenance

### Monitoring Recommendations

1. **Google Cloud Monitoring**: Enable GKE monitoring and logging
2. **Application Metrics**: Use Spring Boot Actuator metrics endpoint
3. **Log Aggregation**: Configure Cloud Logging for centralized logs
4. **Alerting**: Set up alerts for pod failures, high CPU/memory usage

### Backup and Disaster Recovery

1. **Configuration Backup**: Store Kubernetes manifests in version control
2. **Data Backup**: Implement backup strategy for persistent data
3. **Disaster Recovery Plan**: Document recovery procedures
4. **Regular Testing**: Test backup and restore procedures

### Maintenance Tasks

1. **Regular Updates**: Keep dependencies and base images updated
2. **Security Patches**: Apply security patches promptly
3. **Performance Tuning**: Monitor and optimize resource usage
4. **Capacity Planning**: Review and adjust cluster size as needed

---

## Conclusion

This deployment guide provides comprehensive instructions for deploying ResortsLite to Google Kubernetes Engine. Follow the steps carefully, and refer to the troubleshooting section for common issues. For production deployments, ensure all security considerations are implemented and monitoring is properly configured.

For questions or issues, consult the official documentation links provided or contact your DevOps team.
