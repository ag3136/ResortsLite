# ResortsLite — Deployment Guide

## Overview

**Application**: ResortsLite  
**Framework**: Spring Boot 2.7.18 (Java 8)  
**Build Tool**: Maven  
**Target Platform**: AWS EKS (Elastic Kubernetes Service)  
**Container Registry**: AWS ECR or Docker Hub  

ResortsLite is a legacy resort booking REST API modernised for cloud-native deployment on AWS EKS. It uses Spring Session with Redis (Amazon ElastiCache) for externalized session storage, Spring Boot Actuator for health probes, and environment-variable-driven configuration for all external service endpoints.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Project Structure](#project-structure)
3. [Local Development with Docker Compose](#local-development-with-docker-compose)
4. [Build and Push Docker Image](#build-and-push-docker-image)
5. [AWS EKS Prerequisites](#aws-eks-prerequisites)
6. [EKS Cluster Setup](#eks-cluster-setup)
7. [Kubernetes Deployment](#kubernetes-deployment)
8. [Configuration Management](#configuration-management)
9. [Scaling and Management](#scaling-and-management)
10. [Troubleshooting](#troubleshooting)
11. [Security Considerations](#security-considerations)

---

## Prerequisites

### Local Development
| Tool | Version | Purpose |
|------|---------|---------|
| Docker | 24.x+ | Build and run containers |
| Docker Compose | 2.x+ | Local multi-container orchestration |
| Java JDK | 8+ | Local build (optional) |
| Maven | 3.8+ | Local build (optional) |

### AWS EKS Deployment
| Tool | Version | Purpose |
|------|---------|---------|
| AWS CLI | 2.x | AWS authentication and ECR access |
| kubectl | 1.27+ | Kubernetes cluster management |
| eksctl | 0.160+ | EKS cluster provisioning (optional) |

### IAM Permissions Required
- `ecr:GetAuthorizationToken`
- `ecr:BatchCheckLayerAvailability`
- `ecr:PutImage`
- `ecr:InitiateLayerUpload`
- `ecr:UploadLayerPart`
- `ecr:CompleteLayerUpload`
- `ecr:CreateRepository`
- `eks:DescribeCluster`
- `eks:UpdateKubeconfig`

---

## Project Structure

```
RLBRD/
├── Dockerfile                  # Multi-stage build (Java 8 / amazoncorretto:8)
├── docker-compose.yml          # Local development (app only)
├── .dockerignore               # Excludes build artifacts and wrapper files
├── pom.xml                     # Maven build descriptor
├── src/
│   └── main/
│       ├── java/com/demo/resortslite/
│       │   ├── ResortsLiteApplication.java
│       │   ├── BookingController.java
│       │   ├── BookingService.java
│       │   └── ReportService.java
│       └── resources/
│           └── application.properties
├── kubernetes/
│   ├── namespace.yaml
│   ├── deployment.yaml
│   ├── service.yaml
│   └── ingress.yaml
├── scripts/
│   ├── build-push.sh           # Linux/macOS build & push
│   ├── build-push.bat          # Windows build & push
│   ├── deploy-image.sh         # Linux/macOS EKS deploy
│   └── deploy-image.bat        # Windows EKS deploy
└── docs/
    └── DEPLOYMENT.md           # This file
```

---

## Local Development with Docker Compose

Docker Compose runs **only the application container**. External services (Redis, databases) must be provided separately or mocked.

### 1. Configure environment variables

Create a `.env` file in the project root:

```dotenv
REDIS_HOST=your-elasticache-endpoint.cache.amazonaws.com
REDIS_PORT=6379
PAYMENT_API_URL=http://payment-service.default.svc.cluster.local:9090/payments/charge
REPORT_BASE_PATH=/var/reports
BACKUP_PATH=/var/backups/resorts
CACHE_PROVIDER=redis
```

### 2. Start the application

```bash
docker compose up --build
```

### 3. Verify the application

```bash
# Health check
curl http://localhost:8080/actuator/health

# Test booking endpoint
curl -X POST "http://localhost:8080/api/bookings/create?guestName=John&roomType=SUITE&checkIn=2024-06-01&checkOut=2024-06-05"
```

### 4. Stop the application

```bash
docker compose down
```

---

## Build and Push Docker Image

### Linux / macOS

```bash
chmod +x scripts/build-push.sh
./scripts/build-push.sh
```

### Windows

```cmd
scripts\build-push.bat
```

The script will prompt you to:
1. Select registry type (AWS ECR or Docker Hub)
2. Enter registry credentials / AWS account details
3. Enter an image tag (defaults to `latest`)

The script automatically:
- Sanitizes the image name to lowercase with hyphens
- Creates the ECR repository if it does not exist (ECR only)
- Builds the Docker image from the project root
- Pushes the image to the selected registry

---

## AWS EKS Prerequisites

### 1. Configure AWS CLI

```bash
aws configure
# Enter: AWS Access Key ID, Secret Access Key, Default region, Output format
```

### 2. Verify AWS identity

```bash
aws sts get-caller-identity
```

### 3. Install kubectl

```bash
# macOS
brew install kubectl

# Linux
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
chmod +x kubectl && sudo mv kubectl /usr/local/bin/

# Windows (via Chocolatey)
choco install kubernetes-cli
```

### 4. Install AWS Load Balancer Controller (required for Ingress)

The Ingress manifest uses the AWS Load Balancer Controller. Install it on your EKS cluster:

```bash
# Add the EKS chart repository
helm repo add eks https://aws.github.io/eks-charts
helm repo update

# Install the controller
helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=<YOUR_CLUSTER_NAME> \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller
```

---

## EKS Cluster Setup

### Option A: Use an existing cluster

```bash
aws eks update-kubeconfig --region <AWS_REGION> --name <CLUSTER_NAME>
kubectl cluster-info
```

### Option B: Create a new cluster with eksctl

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

---

## Kubernetes Deployment

### Automated deployment (recommended)

#### Linux / macOS

```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

#### Windows

```cmd
scripts\deploy-image.bat
```

The script will prompt for:
- AWS region and EKS cluster name
- Full Docker image URI (e.g. `123456789.dkr.ecr.us-east-1.amazonaws.com/resortslite:latest`)
- Application environment variables (Redis host/port, payment API URL, etc.)

### Manual deployment

```bash
# 1. Update the image URI in deployment.yaml
sed -i 's|{{IMAGE_URI}}|YOUR_IMAGE_URI|g' kubernetes/deployment.yaml

# 2. Update environment variable placeholders
sed -i 's|{{REDIS_HOST}}|your-redis-host|g' kubernetes/deployment.yaml
sed -i 's|{{REDIS_PORT}}|6379|g' kubernetes/deployment.yaml
sed -i 's|{{PAYMENT_API_URL}}|http://payment-service:9090/payments/charge|g' kubernetes/deployment.yaml
sed -i 's|{{REPORT_BASE_PATH}}|/var/reports|g' kubernetes/deployment.yaml
sed -i 's|{{BACKUP_PATH}}|/var/backups/resorts|g' kubernetes/deployment.yaml
sed -i 's|{{CACHE_PROVIDER}}|redis|g' kubernetes/deployment.yaml

# 3. Apply manifests in order
kubectl apply -f kubernetes/namespace.yaml
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

# 4. Wait for rollout
kubectl rollout status deployment/resortslite -n resortslite

# 5. Verify
kubectl get pods,svc,ingress -n resortslite
```

### Kubernetes Manifest Descriptions

| File | Kind | Description |
|------|------|-------------|
| `namespace.yaml` | Namespace | Isolates all ResortsLite resources in the `resortslite` namespace |
| `deployment.yaml` | Deployment | Runs 2 replicas with liveness/readiness probes on `/actuator/health` |
| `service.yaml` | Service (ClusterIP) | Internal service exposing port 80 → container port 8080 |
| `ingress.yaml` | Ingress (ALB) | Internet-facing AWS ALB routing traffic to the service |

---

## Configuration Management

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8080` | Application HTTP port |
| `SPRING_PROFILES_ACTIVE` | `docker` | Active Spring profile |
| `REDIS_HOST` | `localhost` | Redis / ElastiCache hostname |
| `REDIS_PORT` | `6379` | Redis port |
| `PAYMENT_API_URL` | `http://payment-service...` | Payment microservice endpoint |
| `REPORT_BASE_PATH` | `/var/reports` | Report file storage path |
| `BACKUP_PATH` | `/var/backups/resorts` | Backup file storage path |
| `CACHE_PROVIDER` | `redis` | Cache provider type |
| `JAVA_OPTS` | `-Xms256m -Xmx512m ...` | JVM tuning flags |

### Using Kubernetes ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: resortslite-config
  namespace: resortslite
data:
  REDIS_HOST: "your-elasticache.cache.amazonaws.com"
  REDIS_PORT: "6379"
  PAYMENT_API_URL: "http://payment-service.default.svc.cluster.local:9090/payments/charge"
  REPORT_BASE_PATH: "/var/reports"
  BACKUP_PATH: "/var/backups/resorts"
  CACHE_PROVIDER: "redis"
```

Apply and reference in deployment:
```bash
kubectl apply -f configmap.yaml
```

### Using Kubernetes Secrets (for sensitive values)

```bash
kubectl create secret generic resortslite-secrets \
  --from-literal=DB_PASSWORD='your-db-password' \
  -n resortslite
```

---

## Scaling and Management

### Horizontal scaling

```bash
# Scale to 4 replicas
kubectl scale deployment resortslite --replicas=4 -n resortslite

# Check status
kubectl get pods -n resortslite
```

### Horizontal Pod Autoscaler (HPA)

```bash
kubectl autoscale deployment resortslite \
  --cpu-percent=70 \
  --min=2 \
  --max=10 \
  -n resortslite
```

### Rolling update

```bash
# Update image
kubectl set image deployment/resortslite \
  resortslite=YOUR_NEW_IMAGE_URI \
  -n resortslite

# Monitor rollout
kubectl rollout status deployment/resortslite -n resortslite
```

### Rollback

```bash
# Rollback to previous version
kubectl rollout undo deployment/resortslite -n resortslite

# Rollback to specific revision
kubectl rollout undo deployment/resortslite --to-revision=2 -n resortslite

# View rollout history
kubectl rollout history deployment/resortslite -n resortslite
```

---

## Troubleshooting

### Pod not starting

```bash
# Check pod status
kubectl get pods -n resortslite

# Describe pod for events
kubectl describe pod <POD_NAME> -n resortslite

# View pod logs
kubectl logs <POD_NAME> -n resortslite

# View previous container logs (if crashed)
kubectl logs <POD_NAME> -n resortslite --previous
```

### Common issues

| Symptom | Likely Cause | Resolution |
|---------|-------------|------------|
| `CrashLoopBackOff` | JVM OOM or missing env var | Check logs; increase memory limits; verify env vars |
| `ImagePullBackOff` | Wrong image URI or missing ECR permissions | Verify image URI; check IAM role for node group |
| `Pending` pods | Insufficient cluster resources | Scale node group or reduce resource requests |
| Health probe failing | App not started within `initialDelaySeconds` | Increase `initialDelaySeconds` in deployment.yaml |
| Redis connection refused | Wrong `REDIS_HOST` | Verify ElastiCache endpoint and security group rules |

### Health check

```bash
# Port-forward to test locally
kubectl port-forward svc/resortslite-service 8080:80 -n resortslite

# In another terminal
curl http://localhost:8080/actuator/health
```

### Ingress / ALB issues

```bash
# Check ingress status
kubectl describe ingress resortslite-ingress -n resortslite

# Check AWS Load Balancer Controller logs
kubectl logs -n kube-system -l app.kubernetes.io/name=aws-load-balancer-controller
```

---

## Security Considerations

1. **Non-root container**: The application runs as `appuser` (non-root) inside the container.
2. **Secrets management**: Use AWS Secrets Manager or Kubernetes Secrets for sensitive values (DB passwords, API keys). Never hardcode credentials.
3. **Network policies**: Apply Kubernetes NetworkPolicies to restrict pod-to-pod communication.
4. **Image scanning**: Enable ECR image scanning to detect vulnerabilities in the container image.
5. **RBAC**: Apply least-privilege IAM roles to EKS node groups and service accounts.
6. **TLS**: Configure HTTPS on the ALB Ingress using ACM certificates:
   ```yaml
   alb.ingress.kubernetes.io/certificate-arn: arn:aws:acm:us-east-1:ACCOUNT:certificate/CERT-ID
   alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS": 443}]'
   ```
7. **Dependency vulnerabilities**: The current `pom.xml` includes `log4j-core:2.14.1` (CVE-2021-44228) and `commons-collections:3.2.1` (CVE-2015-6420). **Upgrade these dependencies before production deployment.**

---

## Java-Specific Notes

- **JVM container awareness**: The Dockerfile sets `-XX:+UseContainerSupport` and `-XX:MaxRAMPercentage=75.0` so the JVM respects container memory limits.
- **Startup time**: Spring Boot on Java 8 typically takes 20–40 seconds to start. The `initialDelaySeconds: 60` in the liveness probe accounts for this.
- **Spring profiles**: Set `SPRING_PROFILES_ACTIVE=docker` to activate the Docker/cloud profile.
- **Actuator endpoints**: `/actuator/health` and `/actuator/info` are exposed for Kubernetes probes and ALB health checks.
- **Session storage**: Spring Session is configured to use Redis (Amazon ElastiCache). Ensure the EKS node security group allows outbound traffic to the ElastiCache security group on port 6379.
- **H2 console**: The H2 in-memory database console is enabled (`/h2-console`) for development. Disable it in production by setting `spring.h2.console.enabled=false`.
