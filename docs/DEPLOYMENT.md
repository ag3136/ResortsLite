# BRDresort — Deployment Guide

## Overview

This guide covers building, containerizing, and deploying the **BRDresort** (ResortsLite) Spring Boot application to **Azure Kubernetes Service (AKS)**.

- **Application**: ResortsLite — resort booking REST API
- **Framework**: Spring Boot 2.7.x
- **Java Version**: Java 8
- **Build Tool**: Maven
- **Target Platform**: Azure AKS

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Project Structure](#project-structure)
3. [Local Development with Docker Compose](#local-development-with-docker-compose)
4. [Build and Push Docker Image](#build-and-push-docker-image)
5. [Azure AKS Deployment](#azure-aks-deployment)
6. [Environment Variables Reference](#environment-variables-reference)
7. [Kubernetes Manifest Descriptions](#kubernetes-manifest-descriptions)
8. [Scaling and Management](#scaling-and-management)
9. [Troubleshooting](#troubleshooting)
10. [Security Considerations](#security-considerations)

---

## Prerequisites

### Local Development
- Docker Desktop 24.x or later
- Docker Compose v2.x or later
- Java 8 JDK (for local builds outside Docker)
- Maven 3.8.x or later (for local builds outside Docker)

### Azure AKS Deployment
- Azure CLI (`az`) 2.50+  
  Install: https://docs.microsoft.com/en-us/cli/azure/install-azure-cli
- `kubectl` 1.27+  
  Install: `az aks install-cli`
- An active Azure subscription
- An Azure Container Registry (ACR) or Docker Hub account
- An AKS cluster (see [AKS Cluster Setup](#aks-cluster-setup))

---

## Project Structure

```
BRDresort/
├── Dockerfile                  # Multi-stage Docker build
├── docker-compose.yml          # Local development compose file
├── .dockerignore               # Files excluded from Docker build context
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
│   ├── build-push.sh           # Linux/macOS build & push script
│   ├── build-push.bat          # Windows build & push script
│   ├── deploy-image.sh         # Linux/macOS AKS deploy script
│   └── deploy-image.bat        # Windows AKS deploy script
└── docs/
    └── DEPLOYMENT.md           # This file
```

---

## Local Development with Docker Compose

### 1. Configure Environment Variables

Create a `.env` file in the project root (never commit this file):

```env
SERVER_PORT=8080
REDIS_HOST=your-redis-host
REDIS_PORT=6379
REDIS_PASSWORD=your-redis-password
AZURE_BLOB_REPORTS_URL=https://youraccount.blob.core.windows.net/reports
AZURE_BLOB_BACKUP_URL=https://youraccount.blob.core.windows.net/backups
PAYMENT_API_URL=http://payment-service/payments/charge
```

> **Note**: Redis and other external services must be running and accessible. The `docker-compose.yml` contains only the application container. Provide external service endpoints via environment variables.

### 2. Build and Start the Application

```bash
# Build and start
docker compose up --build

# Run in background
docker compose up --build -d

# View logs
docker compose logs -f brdresort

# Stop
docker compose down
```

### 3. Verify the Application

```bash
# Health check
curl http://localhost:8080/actuator/health

# Create a booking
curl -X POST "http://localhost:8080/api/bookings/create?guestName=John&roomType=DELUXE&checkIn=2024-06-01&checkOut=2024-06-05"

# Check booking status
curl "http://localhost:8080/api/bookings/status/BK-XXXXXXXX"

# Check room availability
curl "http://localhost:8080/api/bookings/availability?roomType=SUITE"
```

---

## Build and Push Docker Image

### Linux / macOS

```bash
chmod +x scripts/build-push.sh
./scripts/build-push.sh
```

The script will prompt you to:
1. Enter an image tag (default: `latest`)
2. Select registry type (Azure ACR or Docker Hub)
3. Provide registry credentials

### Windows

```cmd
scripts\build-push.bat
```

### Manual Build (Advanced)

```bash
# Build image
docker build -t brdresort:latest .

# Tag for ACR
docker tag brdresort:latest <acr-name>.azurecr.io/brdresort:latest

# Login to ACR
az acr login --name <acr-name>

# Push
docker push <acr-name>.azurecr.io/brdresort:latest
```

---

## Azure AKS Deployment

### AKS Cluster Setup

If you don't have an AKS cluster, create one:

```bash
# Login to Azure
az login

# Create resource group
az group create --name brdresort-rg --location eastus

# Create ACR
az acr create --resource-group brdresort-rg --name brdresortacr --sku Basic

# Create AKS cluster with ACR integration
az aks create \
  --resource-group brdresort-rg \
  --name brdresort-aks \
  --node-count 2 \
  --node-vm-size Standard_DS2_v2 \
  --attach-acr brdresortacr \
  --enable-addons ingress-appgw \
  --appgw-name brdresort-appgw \
  --appgw-subnet-cidr "10.225.0.0/16" \
  --generate-ssh-keys

# Get credentials
az aks get-credentials --resource-group brdresort-rg --name brdresort-aks
```

### Deploy Using Script

#### Linux / macOS

```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

The script will prompt for:
- Azure Resource Group name
- AKS Cluster name
- Full Docker image URI (e.g., `brdresortacr.azurecr.io/brdresort:latest`)
- Environment variable values (Redis, Azure Blob, Payment API)

#### Windows

```cmd
scripts\deploy-image.bat
```

### Manual Deployment (Advanced)

```bash
# 1. Configure kubectl
az aks get-credentials --resource-group brdresort-rg --name brdresort-aks

# 2. Update deployment.yaml with your image URI
sed -i 's|{{IMAGE_URI}}|brdresortacr.azurecr.io/brdresort:latest|g' kubernetes/deployment.yaml
sed -i 's|{{REDIS_HOST}}|your-redis-host|g' kubernetes/deployment.yaml
sed -i 's|{{REDIS_PORT}}|6379|g' kubernetes/deployment.yaml
sed -i 's|{{REDIS_PASSWORD}}|your-redis-password|g' kubernetes/deployment.yaml
sed -i 's|{{AZURE_BLOB_REPORTS_URL}}|https://youraccount.blob.core.windows.net/reports|g' kubernetes/deployment.yaml
sed -i 's|{{AZURE_BLOB_BACKUP_URL}}|https://youraccount.blob.core.windows.net/backups|g' kubernetes/deployment.yaml
sed -i 's|{{PAYMENT_API_URL}}|http://payment-service/payments/charge|g' kubernetes/deployment.yaml

# 3. Apply manifests in order
kubectl apply -f kubernetes/namespace.yaml
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

# 4. Wait for rollout
kubectl rollout status deployment/brdresort -n brdresort

# 5. Verify
kubectl get pods,svc,ingress -n brdresort
```

---

## Environment Variables Reference

| Variable | Description | Default |
|---|---|---|
| `SERVER_PORT` | Application HTTP port | `8080` |
| `SPRING_PROFILES_ACTIVE` | Spring profile | `docker` |
| `REDIS_HOST` | Redis server hostname | `localhost` |
| `REDIS_PORT` | Redis server port | `6379` |
| `REDIS_PASSWORD` | Redis authentication password | _(empty)_ |
| `AZURE_BLOB_REPORTS_URL` | Azure Blob Storage URL for reports | _(see properties)_ |
| `AZURE_BLOB_BACKUP_URL` | Azure Blob Storage URL for backups | _(see properties)_ |
| `PAYMENT_API_URL` | Payment service endpoint URL | `http://payment-service/payments/charge` |
| `JAVA_OPTS` | JVM options | `-Xms256m -Xmx512m -XX:+UseContainerSupport` |
| `TZ` | Container timezone | `UTC` |

---

## Kubernetes Manifest Descriptions

### `namespace.yaml`
Creates the `brdresort` namespace to isolate all application resources.

### `deployment.yaml`
Deploys 2 replicas of the BRDresort container with:
- Resource requests: 250m CPU, 512Mi memory
- Resource limits: 500m CPU, 1Gi memory
- Liveness probe: `GET /actuator/health` (initial delay: 60s, period: 30s)
- Readiness probe: `GET /actuator/health` (initial delay: 30s, period: 15s)
- Graceful shutdown: 60-second termination grace period

### `service.yaml`
Exposes the deployment internally via a `ClusterIP` service on port 80 → 8080.

### `ingress.yaml`
Routes external HTTP traffic to the service using the Azure Application Gateway Ingress Controller (AGIC). Update the `host` field to your actual domain.

---

## Scaling and Management

### Manual Scaling

```bash
# Scale to 3 replicas
kubectl scale deployment brdresort --replicas=3 -n brdresort

# Check scaling status
kubectl get pods -n brdresort -w
```

### Horizontal Pod Autoscaler (HPA)

```bash
kubectl autoscale deployment brdresort \
  --cpu-percent=70 \
  --min=2 \
  --max=10 \
  -n brdresort

kubectl get hpa -n brdresort
```

### Rolling Update

```bash
# Update image
kubectl set image deployment/brdresort \
  brdresort=brdresortacr.azurecr.io/brdresort:v2.0.0 \
  -n brdresort

# Monitor rollout
kubectl rollout status deployment/brdresort -n brdresort
```

### Rollback

```bash
# Rollback to previous version
kubectl rollout undo deployment/brdresort -n brdresort

# Rollback to specific revision
kubectl rollout history deployment/brdresort -n brdresort
kubectl rollout undo deployment/brdresort --to-revision=2 -n brdresort
```

---

## Troubleshooting

### Pod Not Starting

```bash
# Check pod status
kubectl get pods -n brdresort

# Describe pod for events
kubectl describe pod <pod-name> -n brdresort

# View pod logs
kubectl logs <pod-name> -n brdresort

# View previous container logs (if crashed)
kubectl logs <pod-name> -n brdresort --previous
```

### Common Issues

**CrashLoopBackOff**
- Check logs: `kubectl logs <pod-name> -n brdresort`
- Verify Redis connectivity (REDIS_HOST, REDIS_PORT, REDIS_PASSWORD)
- Ensure image was pushed successfully to the registry

**ImagePullBackOff**
- Verify the image URI is correct
- Ensure AKS has pull access to ACR: `az aks update --attach-acr <acr-name> -n <aks-name> -g <rg>`
- Check image exists: `az acr repository show-tags --name <acr-name> --repository brdresort`

**Health Check Failures**
- JVM startup can take 30–60 seconds; `initialDelaySeconds` is set accordingly
- Verify `/actuator/health` returns HTTP 200
- Check Redis connection — Spring Session requires Redis to be reachable at startup

**Ingress Not Accessible**
- Verify AGIC add-on is enabled: `az aks show -n <aks-name> -g <rg> --query addonProfiles.ingressApplicationGateway`
- Check ingress status: `kubectl describe ingress brdresort-ingress -n brdresort`
- Ensure DNS points to the Application Gateway public IP

### Useful Commands

```bash
# Get all resources in namespace
kubectl get all -n brdresort

# Execute shell in running pod
kubectl exec -it <pod-name> -n brdresort -- /bin/sh

# Port-forward for local testing
kubectl port-forward svc/brdresort-service 8080:80 -n brdresort

# View resource usage
kubectl top pods -n brdresort
```

---

## Security Considerations

1. **Secrets Management**: Store sensitive values (Redis password, Azure connection strings) in Kubernetes Secrets, not plain ConfigMaps:
   ```bash
   kubectl create secret generic brdresort-secrets \
     --from-literal=REDIS_PASSWORD=<password> \
     -n brdresort
   ```
   Reference in deployment.yaml using `secretKeyRef`.

2. **Non-Root Container**: The Dockerfile runs the application as a non-root user (`appuser`) for security.

3. **Image Scanning**: Enable ACR vulnerability scanning:
   ```bash
   az acr task create --registry <acr-name> --name scan-on-push \
     --image brdresort:{{.Run.ID}} --context /dev/null \
     --file /dev/null --commit-trigger-enabled false
   ```

4. **Network Policies**: Consider adding Kubernetes NetworkPolicy resources to restrict pod-to-pod communication.

5. **RBAC**: Use Azure AD integration with AKS for role-based access control.

6. **TLS/HTTPS**: Configure TLS on the ingress using cert-manager or Azure Application Gateway SSL certificates.

7. **Dependency Vulnerabilities**: The current `pom.xml` includes known vulnerable dependencies (`log4j-core 2.14.1` — CVE-2021-44228, `commons-collections 3.2.1` — CVE-2015-6420). **Upgrade these before production deployment**:
   - `log4j-core` → 2.17.2 or later
   - `commons-collections` → 4.4 or later

---

## Java-Specific Notes

- **JVM Container Awareness**: The Dockerfile sets `-XX:+UseContainerSupport` and `-XX:MaxRAMPercentage=75.0` so the JVM respects container memory limits rather than host memory.
- **Spring Session Redis**: The application uses Spring Session Data Redis for distributed session management. Ensure Redis is available before the application starts.
- **Actuator Endpoints**: `/actuator/health` and `/actuator/info` are exposed for Kubernetes probes and monitoring.
- **H2 In-Memory Database**: The application uses H2 for persistence. For production, replace with a persistent database (Azure Database for PostgreSQL/MySQL) and update `spring.datasource.*` properties.
- **Graceful Shutdown**: The deployment is configured with a 60-second termination grace period to allow in-flight requests to complete.
