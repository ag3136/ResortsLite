# ResortsLite - AWS EKS Deployment Guide

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Local Development Setup](#local-development-setup)
4. [Building and Pushing Docker Image](#building-and-pushing-docker-image)
5. [AWS EKS Deployment](#aws-eks-deployment)
6. [Configuration Management](#configuration-management)
7. [Monitoring and Troubleshooting](#monitoring-and-troubleshooting)
8. [Scaling and Management](#scaling-and-management)
9. [Security Considerations](#security-considerations)
10. [Technology-Specific Notes](#technology-specific-notes)

---

## Overview

ResortsLite is a Spring Boot 2.7.x application built with Java 8, designed for containerized deployment on AWS EKS (Elastic Kubernetes Service). This guide provides comprehensive instructions for building, deploying, and managing the application in a cloud-native environment.

**Application Details:**
- **Framework:** Spring Boot 2.7.18
- **Java Version:** Java 8 (1.8)
- **Build Tool:** Maven
- **Application Port:** 8080
- **Health Endpoint:** /actuator/health
- **Session Storage:** Redis (externalized)
- **Caching:** Redis (distributed)

---

## Prerequisites

### Required Tools

1. **Docker** (version 20.10 or later)
   - Download: https://www.docker.com/products/docker-desktop
   - Verify: `docker --version`

2. **AWS CLI** (version 2.x)
   - Download: https://aws.amazon.com/cli/
   - Verify: `aws --version`
   - Configure: `aws configure`

3. **kubectl** (version 1.24 or later)
   - Download: https://kubernetes.io/docs/tasks/tools/
   - Verify: `kubectl version --client`

4. **eksctl** (optional, for cluster creation)
   - Download: https://eksctl.io/
   - Verify: `eksctl version`

### AWS Requirements

1. **AWS Account** with appropriate permissions
2. **IAM Permissions:**
   - ECR: Full access for pushing images
   - EKS: Full access for cluster management
   - EC2: Access for node management
   - VPC: Access for networking

3. **EKS Cluster** (if not already created)
   - Kubernetes version 1.24 or later
   - At least 2 worker nodes (t3.medium or larger)
   - AWS Load Balancer Controller installed

4. **External Services:**
   - **Redis/ElastiCache:** For session storage and caching
   - **Database:** H2 (in-memory) or external database (RDS)
   - **Payment Service:** External payment processing service
   - **Inventory Service:** External inventory management service
   - **Notification Service:** External notification service

---

## Local Development Setup

### 1. Clone the Repository

```bash
cd /path/to/project
```

### 2. Build the Application Locally

```bash
# Using Maven
mvn clean package -DskipTests

# Verify the JAR file
ls -lh target/*.jar
```

### 3. Run with Docker Compose

```bash
# Build and start the application
docker-compose up --build

# Access the application
# Application: http://localhost:8080
# Health Check: http://localhost:8080/actuator/health
# H2 Console: http://localhost:8080/h2-console

# Stop the application
docker-compose down
```

### 4. Environment Variables for Local Development

Create a `.env` file in the project root:

```env
# Redis Configuration
REDIS_HOST=redis.example.com
REDIS_PORT=6379
REDIS_PASSWORD=

# Database Configuration
SPRING_DATASOURCE_URL=jdbc:h2:mem:resortdb;DB_CLOSE_DELAY=-1
SPRING_DATASOURCE_USERNAME=sa
SPRING_DATASOURCE_PASSWORD=

# External Services
PAYMENT_SERVICE_URL=http://payment-service:9090/payments/charge
APP_PAYMENT_ENDPOINT=http://payment-svc.internal:9090/charge
APP_INVENTORY_ENDPOINT=http://inventory-svc.internal:8081/rooms
APP_NOTIFICATION_ENDPOINT=http://notify.internal:7070/send
```

---

## Building and Pushing Docker Image

### Option 1: Using build-push.sh (Linux/macOS)

```bash
# Make the script executable
chmod +x scripts/build-push.sh

# Run the script
./scripts/build-push.sh
```

**Script Workflow:**
1. Prompts for image tag (default: latest)
2. Asks to select registry (AWS ECR or Docker Hub)
3. Prompts for registry credentials
4. Builds the Docker image
5. Pushes to the selected registry

### Option 2: Using build-push.bat (Windows)

```cmd
# Run the script
scripts\build-push.bat
```

### Manual Build and Push

#### AWS ECR

```bash
# Set variables
AWS_REGION=us-east-1
AWS_ACCOUNT_ID=123456789012
ECR_REPO=resortslite
IMAGE_TAG=latest

# Authenticate with ECR
aws ecr get-login-password --region $AWS_REGION | \
  docker login --username AWS --password-stdin \
  $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

# Create ECR repository (if not exists)
aws ecr create-repository --repository-name $ECR_REPO --region $AWS_REGION

# Build image
docker build -t $ECR_REPO:$IMAGE_TAG .

# Tag image
docker tag $ECR_REPO:$IMAGE_TAG \
  $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPO:$IMAGE_TAG

# Push image
docker push $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPO:$IMAGE_TAG
```

#### Docker Hub

```bash
# Set variables
DOCKER_USERNAME=yourusername
IMAGE_NAME=resortslite
IMAGE_TAG=latest

# Login to Docker Hub
docker login -u $DOCKER_USERNAME

# Build image
docker build -t $DOCKER_USERNAME/$IMAGE_NAME:$IMAGE_TAG .

# Push image
docker push $DOCKER_USERNAME/$IMAGE_NAME:$IMAGE_TAG
```

---

## AWS EKS Deployment

### Prerequisites for EKS Deployment

1. **EKS Cluster Setup**

```bash
# Create EKS cluster (if not exists)
eksctl create cluster \
  --name resortslite-cluster \
  --region us-east-1 \
  --nodegroup-name standard-workers \
  --node-type t3.medium \
  --nodes 2 \
  --nodes-min 2 \
  --nodes-max 4 \
  --managed

# Configure kubectl
aws eks update-kubeconfig --region us-east-1 --name resortslite-cluster

# Verify cluster access
kubectl cluster-info
kubectl get nodes
```

2. **Install AWS Load Balancer Controller**

```bash
# Create IAM policy
curl -o iam_policy.json https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/v2.4.7/docs/install/iam_policy.json

aws iam create-policy \
  --policy-name AWSLoadBalancerControllerIAMPolicy \
  --policy-document file://iam_policy.json

# Create service account
eksctl create iamserviceaccount \
  --cluster=resortslite-cluster \
  --namespace=kube-system \
  --name=aws-load-balancer-controller \
  --attach-policy-arn=arn:aws:iam::$AWS_ACCOUNT_ID:policy/AWSLoadBalancerControllerIAMPolicy \
  --override-existing-serviceaccounts \
  --approve

# Install controller using Helm
helm repo add eks https://aws.github.io/eks-charts
helm repo update

helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=resortslite-cluster \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller
```

### Deployment Using Scripts

#### Option 1: Using deploy-image.sh (Linux/macOS)

```bash
# Make the script executable
chmod +x scripts/deploy-image.sh

# Run the script
./scripts/deploy-image.sh
```

**Script Workflow:**
1. Prompts for AWS region and EKS cluster name
2. Prompts for Docker image URI
3. Prompts for environment variables (database, Redis, external services)
4. Configures kubectl for EKS
5. Updates Kubernetes manifests with provided values
6. Applies manifests in order (namespace → deployment → service → ingress)
7. Waits for deployment rollout
8. Displays deployment information and access URL

#### Option 2: Using deploy-image.bat (Windows)

```cmd
# Run the script
scripts\deploy-image.bat
```

### Manual Deployment

1. **Update Kubernetes Manifests**

Edit `kubernetes/deployment.yaml` and replace placeholders:

```yaml
# Replace {{IMAGE_URI}} with your actual image URI
image: 123456789012.dkr.ecr.us-east-1.amazonaws.com/resortslite:latest

# Update environment variables
env:
- name: REDIS_HOST
  value: "your-redis-host.cache.amazonaws.com"
- name: REDIS_PORT
  value: "6379"
# ... other environment variables
```

2. **Apply Manifests**

```bash
# Create namespace
kubectl apply -f kubernetes/namespace.yaml

# Deploy application
kubectl apply -f kubernetes/deployment.yaml

# Create service
kubectl apply -f kubernetes/service.yaml

# Create ingress
kubectl apply -f kubernetes/ingress.yaml
```

3. **Verify Deployment**

```bash
# Check deployment status
kubectl rollout status deployment/resortslite -n resortslite

# View pods
kubectl get pods -n resortslite

# View services
kubectl get svc -n resortslite

# View ingress
kubectl get ingress -n resortslite
```

4. **Get Application URL**

```bash
# Get Load Balancer URL
kubectl get ingress resortslite-ingress -n resortslite -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'

# Access the application
# http://<load-balancer-url>/
# http://<load-balancer-url>/actuator/health
```

---

## Configuration Management

### Environment Variables

The application uses the following environment variables:

#### Database Configuration
- `SPRING_DATASOURCE_URL`: JDBC connection URL
- `SPRING_DATASOURCE_USERNAME`: Database username
- `SPRING_DATASOURCE_PASSWORD`: Database password

#### Redis Configuration (Session & Cache)
- `REDIS_HOST`: Redis server hostname
- `REDIS_PORT`: Redis server port (default: 6379)
- `REDIS_PASSWORD`: Redis authentication password

#### External Services
- `PAYMENT_SERVICE_URL`: Payment service endpoint
- `APP_PAYMENT_ENDPOINT`: Legacy payment endpoint
- `APP_INVENTORY_ENDPOINT`: Inventory service endpoint
- `APP_NOTIFICATION_ENDPOINT`: Notification service endpoint

#### File Paths
- `APP_REPORT_BASE_PATH`: Base path for reports (default: /var/reports)
- `APP_BACKUP_PATH`: Base path for backups (default: /var/backups)

#### JVM Configuration
- `JAVA_OPTS`: JVM options (default: -Xmx512m -Xms256m -XX:+UseContainerSupport)

### Using Kubernetes ConfigMaps

```bash
# Create ConfigMap for application properties
kubectl create configmap resortslite-config \
  --from-literal=REDIS_HOST=redis.example.com \
  --from-literal=REDIS_PORT=6379 \
  -n resortslite

# Update deployment to use ConfigMap
kubectl set env deployment/resortslite \
  --from=configmap/resortslite-config \
  -n resortslite
```

### Using Kubernetes Secrets

```bash
# Create Secret for sensitive data
kubectl create secret generic resortslite-secrets \
  --from-literal=SPRING_DATASOURCE_PASSWORD=mypassword \
  --from-literal=REDIS_PASSWORD=redispassword \
  -n resortslite

# Update deployment to use Secret
kubectl set env deployment/resortslite \
  --from=secret/resortslite-secrets \
  -n resortslite
```

---

## Monitoring and Troubleshooting

### Health Checks

```bash
# Check application health
kubectl exec -it <pod-name> -n resortslite -- curl http://localhost:8080/actuator/health

# Check from outside the cluster
curl http://<load-balancer-url>/actuator/health
```

### Viewing Logs

```bash
# View logs for all pods
kubectl logs -n resortslite -l app=resortslite

# View logs for specific pod
kubectl logs -n resortslite <pod-name>

# Follow logs in real-time
kubectl logs -n resortslite -l app=resortslite -f

# View logs from previous container (if crashed)
kubectl logs -n resortslite <pod-name> --previous
```

### Common Issues and Solutions

#### 1. Pod Not Starting

```bash
# Check pod status
kubectl describe pod <pod-name> -n resortslite

# Common causes:
# - Image pull errors: Check ECR permissions and image URI
# - Resource limits: Check node capacity
# - Configuration errors: Check environment variables
```

#### 2. Application Crashes

```bash
# Check logs
kubectl logs -n resortslite <pod-name>

# Common causes:
# - Redis connection failure: Verify REDIS_HOST and REDIS_PORT
# - Database connection failure: Verify SPRING_DATASOURCE_URL
# - Out of memory: Increase memory limits in deployment.yaml
```

#### 3. Service Not Accessible

```bash
# Check service endpoints
kubectl get endpoints -n resortslite

# Check ingress status
kubectl describe ingress resortslite-ingress -n resortslite

# Common causes:
# - Load Balancer not ready: Wait a few minutes
# - Security group rules: Check AWS security groups
# - Health check failures: Check /actuator/health endpoint
```

#### 4. Redis Connection Issues

```bash
# Test Redis connectivity from pod
kubectl exec -it <pod-name> -n resortslite -- sh
# Inside pod:
# telnet $REDIS_HOST $REDIS_PORT

# Common causes:
# - Incorrect REDIS_HOST: Verify ElastiCache endpoint
# - Security group rules: Allow traffic from EKS nodes
# - Authentication: Verify REDIS_PASSWORD if auth is enabled
```

### Debugging Commands

```bash
# Get pod details
kubectl describe pod <pod-name> -n resortslite

# Execute commands in pod
kubectl exec -it <pod-name> -n resortslite -- /bin/sh

# Check resource usage
kubectl top pods -n resortslite

# Check events
kubectl get events -n resortslite --sort-by='.lastTimestamp'
```

---

## Scaling and Management

### Horizontal Scaling

```bash
# Scale deployment manually
kubectl scale deployment resortslite -n resortslite --replicas=3

# Verify scaling
kubectl get pods -n resortslite
```

### Horizontal Pod Autoscaler (HPA)

```yaml
# Create HPA manifest (hpa.yaml)
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

```bash
# Apply HPA
kubectl apply -f hpa.yaml

# Check HPA status
kubectl get hpa -n resortslite
```

### Rolling Updates

```bash
# Update image
kubectl set image deployment/resortslite \
  resortslite=123456789012.dkr.ecr.us-east-1.amazonaws.com/resortslite:v2.0 \
  -n resortslite

# Check rollout status
kubectl rollout status deployment/resortslite -n resortslite

# View rollout history
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

### Resource Management

```bash
# Update resource limits
kubectl set resources deployment resortslite \
  -n resortslite \
  --limits=cpu=1000m,memory=2Gi \
  --requests=cpu=500m,memory=1Gi
```

---

## Security Considerations

### 1. Image Security

- **Use specific image tags** instead of `latest`
- **Scan images for vulnerabilities** using AWS ECR image scanning
- **Use minimal base images** (e.g., amazoncorretto:8)
- **Run as non-root user** (already configured in Dockerfile)

```bash
# Enable ECR image scanning
aws ecr put-image-scanning-configuration \
  --repository-name resortslite \
  --image-scanning-configuration scanOnPush=true \
  --region us-east-1
```

### 2. Secrets Management

- **Never hardcode secrets** in manifests or code
- **Use Kubernetes Secrets** for sensitive data
- **Consider AWS Secrets Manager** for production

```bash
# Create secret from AWS Secrets Manager
kubectl create secret generic db-credentials \
  --from-literal=username=$(aws secretsmanager get-secret-value --secret-id db-username --query SecretString --output text) \
  --from-literal=password=$(aws secretsmanager get-secret-value --secret-id db-password --query SecretString --output text) \
  -n resortslite
```

### 3. Network Security

- **Use Network Policies** to restrict pod-to-pod communication
- **Configure Security Groups** for EKS nodes
- **Use private subnets** for worker nodes
- **Enable VPC Flow Logs** for network monitoring

### 4. RBAC (Role-Based Access Control)

```yaml
# Create service account with limited permissions
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

### 5. Pod Security

- **Enable Pod Security Standards**
- **Use read-only root filesystem** where possible
- **Drop unnecessary capabilities**
- **Set security context**

---

## Technology-Specific Notes

### Spring Boot 2.7.x Configuration

#### 1. Actuator Endpoints

The application exposes Spring Boot Actuator endpoints for monitoring:

- `/actuator/health` - Health check endpoint (used by Kubernetes probes)
- `/actuator/info` - Application information
- `/actuator/metrics` - Application metrics

#### 2. Spring Profiles

The application supports multiple Spring profiles:

```bash
# Set profile via environment variable
kubectl set env deployment/resortslite \
  SPRING_PROFILES_ACTIVE=production \
  -n resortslite
```

Available profiles:
- `default` - Local development
- `docker` - Docker Compose deployment
- `production` - Production deployment on EKS

#### 3. Redis Session Management

The application uses Spring Session with Redis for externalized session storage:

```properties
spring.session.store-type=redis
spring.session.redis.flush-mode=on_save
spring.session.redis.namespace=spring:session
```

**Benefits:**
- Sessions persist across pod restarts
- Supports horizontal scaling
- No session affinity required

**ElastiCache Setup:**
```bash
# Create ElastiCache Redis cluster
aws elasticache create-cache-cluster \
  --cache-cluster-id resortslite-redis \
  --cache-node-type cache.t3.micro \
  --engine redis \
  --num-cache-nodes 1 \
  --region us-east-1
```

#### 4. JVM Tuning for Containers

The Dockerfile includes JVM options optimized for containers:

```bash
JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
```

**Explanation:**
- `-Xmx512m`: Maximum heap size
- `-Xms256m`: Initial heap size
- `-XX:+UseContainerSupport`: Enable container awareness
- `-XX:MaxRAMPercentage=75.0`: Use 75% of container memory for heap

**Adjust for your workload:**
```bash
# For memory-intensive applications
kubectl set env deployment/resortslite \
  JAVA_OPTS="-Xmx1024m -Xms512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0" \
  -n resortslite
```

#### 5. Logging Configuration

Configure logging for cloud environments:

```yaml
# Add to deployment.yaml
env:
- name: LOGGING_LEVEL_ROOT
  value: "INFO"
- name: LOGGING_LEVEL_COM_DEMO_RESORTSLITE
  value: "DEBUG"
- name: LOGGING_PATTERN_CONSOLE
  value: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
```

#### 6. Database Migration

For production deployments with external databases:

```bash
# Add Flyway or Liquibase dependency to pom.xml
# Configure database migration scripts
# Run migrations on startup
```

### Java 8 Considerations

#### 1. Base Image Selection

The Dockerfile uses `amazoncorretto:8` as specified in the EXPLICIT_BASE_IMAGE parameter:

- **Amazon Corretto 8**: Long-term support, optimized for AWS
- **Security updates**: Regular patches and updates
- **Performance**: Optimized for cloud workloads

#### 2. Memory Management

Java 8 requires explicit container support flags:

```bash
-XX:+UnlockExperimentalVMOptions
-XX:+UseCGroupMemoryLimitForHeap
```

These are included in the JAVA_OPTS configuration.

#### 3. Garbage Collection

For containerized Java 8 applications:

```bash
# Add to JAVA_OPTS for better GC performance
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:ParallelGCThreads=2
-XX:ConcGCThreads=1
```

---

## Additional Resources

### AWS Documentation
- [Amazon EKS User Guide](https://docs.aws.amazon.com/eks/latest/userguide/)
- [Amazon ECR User Guide](https://docs.aws.amazon.com/ecr/latest/userguide/)
- [AWS Load Balancer Controller](https://kubernetes-sigs.github.io/aws-load-balancer-controller/)

### Kubernetes Documentation
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [kubectl Cheat Sheet](https://kubernetes.io/docs/reference/kubectl/cheatsheet/)

### Spring Boot Documentation
- [Spring Boot Reference](https://docs.spring.io/spring-boot/docs/2.7.x/reference/html/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/2.7.x/reference/html/actuator.html)
- [Spring Session](https://docs.spring.io/spring-session/docs/current/reference/html5/)

---

## Support and Troubleshooting

For issues or questions:

1. Check application logs: `kubectl logs -n resortslite -l app=resortslite`
2. Review Kubernetes events: `kubectl get events -n resortslite`
3. Verify external service connectivity (Redis, database, external APIs)
4. Check AWS service status and quotas
5. Review security group rules and network policies

---

**Last Updated:** 2024
**Version:** 1.0.0
**Application:** ResortsLite
**Target Platform:** AWS EKS
