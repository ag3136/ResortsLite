# ResortsLite - Deployment Guide

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Local Development with Docker](#local-development-with-docker)
4. [Building and Pushing Docker Images](#building-and-pushing-docker-images)
5. [AWS EKS Deployment](#aws-eks-deployment)
6. [Configuration Management](#configuration-management)
7. [Monitoring and Health Checks](#monitoring-and-health-checks)
8. [Troubleshooting](#troubleshooting)
9. [Scaling and Management](#scaling-and-management)
10. [Security Considerations](#security-considerations)

---

## Overview

ResortsLite is a Spring Boot 2.7.x application built with Java 8, designed for containerized deployment on AWS EKS (Elastic Kubernetes Service). This guide provides comprehensive instructions for building, deploying, and managing the application in both local and cloud environments.

**Technology Stack:**
- Java 8
- Spring Boot 2.7.18
- Maven 3.x
- Docker
- Kubernetes (AWS EKS)
- Redis (for session management)
- H2 Database (in-memory, for development)

---

## Prerequisites

### Required Tools

#### For Local Development:
- **Docker Desktop** (v20.10+)
  - Download: https://www.docker.com/products/docker-desktop
- **Docker Compose** (v2.0+)
  - Included with Docker Desktop
- **Java 8 JDK** (for local builds)
  - Download: https://adoptium.net/
- **Maven 3.6+** (for local builds)
  - Download: https://maven.apache.org/download.cgi

#### For AWS EKS Deployment:
- **AWS CLI** (v2.x)
  - Install: `curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip" && unzip awscliv2.zip && sudo ./aws/install`
  - Configure: `aws configure`
- **kubectl** (v1.24+)
  - Install: `curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"`
  - Make executable: `chmod +x kubectl && sudo mv kubectl /usr/local/bin/`
- **eksctl** (optional, for cluster creation)
  - Install: `curl --silent --location "https://github.com/weaveworks/eksctl/releases/latest/download/eksctl_$(uname -s)_amd64.tar.gz" | tar xz -C /tmp && sudo mv /tmp/eksctl /usr/local/bin`

#### AWS Permissions Required:
- ECR: `ecr:GetAuthorizationToken`, `ecr:CreateRepository`, `ecr:PutImage`
- EKS: `eks:DescribeCluster`, `eks:ListClusters`
- IAM: Permissions to assume EKS cluster role
- EC2: VPC and networking permissions for EKS

---

## Local Development with Docker

### Step 1: Clone the Repository
```bash
git clone <repository-url>
cd "Resorts Mono"
```

### Step 2: Build the Application Locally (Optional)
```bash
mvn clean package -DskipTests
```

### Step 3: Run with Docker Compose
```bash
docker-compose up --build
```

This will:
- Build the Docker image
- Start the application container
- Expose the application on port 8080

### Step 4: Access the Application
- **Application URL**: http://localhost:8080
- **Health Check**: http://localhost:8080/actuator/health
- **H2 Console**: http://localhost:8080/h2-console

### Step 5: Stop the Application
```bash
docker-compose down
```

### Environment Variables for Local Development

Edit `docker-compose.yml` to customize:
```yaml
environment:
  - REDIS_HOST=your-redis-host
  - REDIS_PORT=6379
  - REDIS_PASSWORD=your-redis-password
  - PAYMENT_API_URL=http://payment-service:9090/payments/charge
```

---

## Building and Pushing Docker Images

### Option 1: Using Build Script (Linux/macOS)

```bash
cd scripts
chmod +x build-push.sh
./build-push.sh
```

**Interactive Prompts:**
1. Enter image tag (default: `latest`)
2. Select registry:
   - **1**: AWS ECR
   - **2**: Docker Hub
3. Provide registry credentials

**For AWS ECR:**
- AWS Region (e.g., `us-east-1`)
- AWS Account ID (e.g., `123456789012`)
- ECR Repository Name (default: `resortslite`)

**For Docker Hub:**
- Docker Hub Username
- Docker Hub Password/Token
- Repository Name (default: `resortslite`)

### Option 2: Using Build Script (Windows)

```cmd
cd scripts
build-push.bat
```

Follow the same interactive prompts as the Linux version.

### Option 3: Manual Build and Push

#### AWS ECR:
```bash
# Authenticate with ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 123456789012.dkr.ecr.us-east-1.amazonaws.com

# Create repository (if not exists)
aws ecr create-repository --repository-name resortslite --region us-east-1

# Build image
docker build -t resortslite:latest .

# Tag image
docker tag resortslite:latest 123456789012.dkr.ecr.us-east-1.amazonaws.com/resortslite:latest

# Push image
docker push 123456789012.dkr.ecr.us-east-1.amazonaws.com/resortslite:latest
```

#### Docker Hub:
```bash
# Login to Docker Hub
docker login -u your-username

# Build image
docker build -t resortslite:latest .

# Tag image
docker tag resortslite:latest your-username/resortslite:latest

# Push image
docker push your-username/resortslite:latest
```

---

## AWS EKS Deployment

### Prerequisites

#### 1. Create EKS Cluster (if not exists)
```bash
eksctl create cluster \
  --name resortslite-cluster \
  --region us-east-1 \
  --nodegroup-name standard-workers \
  --node-type t3.medium \
  --nodes 2 \
  --nodes-min 1 \
  --nodes-max 4 \
  --managed
```

#### 2. Install AWS Load Balancer Controller
```bash
# Create IAM policy
curl -o iam_policy.json https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/v2.4.7/docs/install/iam_policy.json
aws iam create-policy --policy-name AWSLoadBalancerControllerIAMPolicy --policy-document file://iam_policy.json

# Create service account
eksctl create iamserviceaccount \
  --cluster=resortslite-cluster \
  --namespace=kube-system \
  --name=aws-load-balancer-controller \
  --attach-policy-arn=arn:aws:iam::<AWS_ACCOUNT_ID>:policy/AWSLoadBalancerControllerIAMPolicy \
  --approve

# Install controller
kubectl apply -k "github.com/aws/eks-charts/stable/aws-load-balancer-controller//crds?ref=master"
helm repo add eks https://aws.github.io/eks-charts
helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=resortslite-cluster \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller
```

### Deployment Steps

#### Option 1: Using Deployment Script (Linux/macOS)

```bash
cd scripts
chmod +x deploy-image.sh
./deploy-image.sh
```

**Interactive Prompts:**
1. AWS Region (e.g., `us-east-1`)
2. EKS Cluster Name (e.g., `resortslite-cluster`)
3. Docker Image URI (e.g., `123456789012.dkr.ecr.us-east-1.amazonaws.com/resortslite:latest`)
4. Application configuration:
   - Database URL
   - Database credentials
   - Redis host/port/password
   - External service endpoints

#### Option 2: Using Deployment Script (Windows)

```cmd
cd scripts
deploy-image.bat
```

Follow the same interactive prompts as the Linux version.

#### Option 3: Manual Deployment

```bash
# Configure kubectl
aws eks update-kubeconfig --region us-east-1 --name resortslite-cluster

# Update deployment.yaml with your image URI
sed -i 's|{{IMAGE_URI}}|123456789012.dkr.ecr.us-east-1.amazonaws.com/resortslite:latest|g' kubernetes/deployment.yaml

# Update environment variables in deployment.yaml
# Edit kubernetes/deployment.yaml and replace {{PLACEHOLDER}} values

# Apply manifests
kubectl apply -f kubernetes/namespace.yaml
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

# Wait for deployment
kubectl rollout status deployment/resortslite -n resortslite

# Verify deployment
kubectl get pods,svc,ingress -n resortslite
```

### Verify Deployment

```bash
# Check pod status
kubectl get pods -n resortslite

# Check service
kubectl get svc -n resortslite

# Check ingress
kubectl get ingress -n resortslite

# View logs
kubectl logs -n resortslite -l app=resortslite

# Test health endpoint
kubectl port-forward -n resortslite svc/resortslite-service 8080:80
curl http://localhost:8080/actuator/health
```

---

## Configuration Management

### Environment Variables

The application uses the following environment variables:

#### Spring Boot Configuration
- `SPRING_PROFILES_ACTIVE`: Active Spring profile (default: `production`)
- `SERVER_PORT`: Application port (default: `8080`)

#### Database Configuration
- `SPRING_DATASOURCE_URL`: JDBC connection URL
- `SPRING_DATASOURCE_USERNAME`: Database username
- `SPRING_DATASOURCE_PASSWORD`: Database password

#### Redis Configuration (Session Management)
- `REDIS_HOST`: Redis server hostname
- `REDIS_PORT`: Redis server port (default: `6379`)
- `REDIS_PASSWORD`: Redis authentication password

#### External Service Endpoints
- `PAYMENT_API_URL`: Payment service endpoint
- `APP_PAYMENT_ENDPOINT`: Legacy payment endpoint
- `APP_INVENTORY_ENDPOINT`: Inventory service endpoint
- `APP_NOTIFICATION_ENDPOINT`: Notification service endpoint

#### Application Configuration
- `REPORT_BASE_PATH`: Base path for report files (default: `/var/reports`)
- `JAVA_OPTS`: JVM options (default: `-Xmx512m -Xms256m`)

### Kubernetes ConfigMap (Optional)

Create a ConfigMap for non-sensitive configuration:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: resortslite-config
  namespace: resortslite
data:
  SPRING_PROFILES_ACTIVE: "production"
  SERVER_PORT: "8080"
  REPORT_BASE_PATH: "/var/reports"
```

Apply:
```bash
kubectl apply -f configmap.yaml
```

### Kubernetes Secrets (Recommended for Sensitive Data)

Create secrets for sensitive information:

```bash
# Create database secret
kubectl create secret generic db-credentials \
  --from-literal=username=admin \
  --from-literal=password=your-secure-password \
  -n resortslite

# Create Redis secret
kubectl create secret generic redis-credentials \
  --from-literal=password=your-redis-password \
  -n resortslite
```

Update `deployment.yaml` to use secrets:
```yaml
env:
- name: SPRING_DATASOURCE_USERNAME
  valueFrom:
    secretKeyRef:
      name: db-credentials
      key: username
- name: SPRING_DATASOURCE_PASSWORD
  valueFrom:
    secretKeyRef:
      name: db-credentials
      key: password
```

---

## Monitoring and Health Checks

### Health Endpoints

The application exposes Spring Boot Actuator endpoints:

- **Health Check**: `/actuator/health`
- **Info**: `/actuator/info`

### Kubernetes Health Probes

The deployment includes:

**Liveness Probe:**
- Endpoint: `/actuator/health`
- Initial Delay: 60 seconds
- Period: 10 seconds
- Timeout: 5 seconds

**Readiness Probe:**
- Endpoint: `/actuator/health`
- Initial Delay: 30 seconds
- Period: 10 seconds
- Timeout: 5 seconds

### Viewing Logs

```bash
# View all logs
kubectl logs -n resortslite -l app=resortslite

# Follow logs
kubectl logs -n resortslite -l app=resortslite -f

# View logs from specific pod
kubectl logs -n resortslite <pod-name>

# View previous container logs (if crashed)
kubectl logs -n resortslite <pod-name> --previous
```

### Monitoring with AWS CloudWatch

Enable CloudWatch Container Insights:
```bash
aws eks update-cluster-config \
  --region us-east-1 \
  --name resortslite-cluster \
  --logging '{"clusterLogging":[{"types":["api","audit","authenticator","controllerManager","scheduler"],"enabled":true}]}'
```

---

## Troubleshooting

### Common Issues

#### 1. Pods Not Starting

**Check pod status:**
```bash
kubectl get pods -n resortslite
kubectl describe pod -n resortslite <pod-name>
```

**Common causes:**
- Image pull errors (check ECR permissions)
- Resource limits too low
- Configuration errors

#### 2. Image Pull Errors

**Solution:**
```bash
# Verify ECR authentication
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <account-id>.dkr.ecr.us-east-1.amazonaws.com

# Check if image exists
aws ecr describe-images --repository-name resortslite --region us-east-1
```

#### 3. Application Crashes (CrashLoopBackOff)

**Check logs:**
```bash
kubectl logs -n resortslite <pod-name> --previous
```

**Common causes:**
- Database connection failures
- Redis connection failures
- Missing environment variables
- JVM memory issues

**Solution:**
- Verify all environment variables are set correctly
- Check external service connectivity
- Increase memory limits if OOM errors

#### 4. Ingress Not Working

**Check ingress status:**
```bash
kubectl get ingress -n resortslite
kubectl describe ingress resortslite-ingress -n resortslite
```

**Verify AWS Load Balancer Controller:**
```bash
kubectl get pods -n kube-system | grep aws-load-balancer-controller
```

#### 5. Health Check Failures

**Test health endpoint:**
```bash
kubectl port-forward -n resortslite svc/resortslite-service 8080:80
curl http://localhost:8080/actuator/health
```

**Common causes:**
- Application not fully started
- Database connection issues
- Redis connection issues

---

## Scaling and Management

### Manual Scaling

```bash
# Scale to 3 replicas
kubectl scale deployment resortslite -n resortslite --replicas=3

# Verify scaling
kubectl get pods -n resortslite
```

### Horizontal Pod Autoscaler (HPA)

Create HPA based on CPU utilization:

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
```

Apply:
```bash
kubectl apply -f hpa.yaml
```

### Rolling Updates

```bash
# Update image
kubectl set image deployment/resortslite resortslite=<new-image-uri> -n resortslite

# Monitor rollout
kubectl rollout status deployment/resortslite -n resortslite

# Rollback if needed
kubectl rollout undo deployment/resortslite -n resortslite
```

### Resource Management

Update resource limits in `deployment.yaml`:

```yaml
resources:
  requests:
    cpu: "250m"
    memory: "512Mi"
  limits:
    cpu: "500m"
    memory: "1Gi"
```

---

## Security Considerations

### 1. Container Security

- **Non-root user**: Application runs as non-root user `appuser`
- **Read-only filesystem**: Consider adding `readOnlyRootFilesystem: true`
- **Security context**: Drop unnecessary capabilities

### 2. Network Security

- **Network Policies**: Implement Kubernetes Network Policies to restrict traffic
- **TLS/SSL**: Configure HTTPS for ingress with ACM certificates
- **Private subnets**: Deploy pods in private subnets

### 3. Secrets Management

- **AWS Secrets Manager**: Use for sensitive data
- **Kubernetes Secrets**: Encrypt at rest
- **IAM Roles**: Use IRSA (IAM Roles for Service Accounts)

### 4. Image Security

- **Scan images**: Use AWS ECR image scanning
- **Update base images**: Regularly update to patch vulnerabilities
- **Minimal images**: Use distroless or alpine images

### 5. Access Control

- **RBAC**: Implement Kubernetes RBAC policies
- **IAM policies**: Follow least privilege principle
- **Audit logging**: Enable EKS audit logs

---

## Additional Resources

- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/2.7.x/reference/html/)
- [AWS EKS Documentation](https://docs.aws.amazon.com/eks/)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Docker Documentation](https://docs.docker.com/)

---

## Support

For issues or questions:
1. Check application logs: `kubectl logs -n resortslite -l app=resortslite`
2. Review pod events: `kubectl describe pod -n resortslite <pod-name>`
3. Verify configuration: `kubectl get configmap,secret -n resortslite`

---

**Last Updated**: 2024
**Version**: 1.0.0
