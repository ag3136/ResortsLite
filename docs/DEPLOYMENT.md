# ResortsLite — Deployment Guide

## Overview

This guide covers building, containerising, and deploying the **ResortsLite** Spring Boot application (Java 8 / Spring Boot 2.7.x) to **Azure Kubernetes Service (AKS)**.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Project Structure](#project-structure)
3. [Local Development with Docker Compose](#local-development-with-docker-compose)
4. [Build and Push Docker Image](#build-and-push-docker-image)
5. [Azure AKS Deployment](#azure-aks-deployment)
6. [Kubernetes Manifest Reference](#kubernetes-manifest-reference)
7. [Configuration and Environment Variables](#configuration-and-environment-variables)
8. [Health Checks and Monitoring](#health-checks-and-monitoring)
9. [Scaling and Management](#scaling-and-management)
10. [Troubleshooting](#troubleshooting)
11. [Security Considerations](#security-considerations)
12. [Rollback Procedure](#rollback-procedure)

---

## Prerequisites

### Local Development
| Tool | Version | Purpose |
|------|---------|---------|
| Docker Desktop | 24.x+ | Build and run containers |
| Java JDK | 8 (1.8) | Local compilation (optional) |
| Maven | 3.8.x+ | Local build (optional) |

### Azure AKS Deployment
| Tool | Version | Purpose |
|------|---------|---------|
| Azure CLI | 2.50+ | Azure resource management |
| kubectl | 1.27+ | Kubernetes cluster management |
| Docker | 24.x+ | Image build and push |

### Azure Resources Required
- Azure Subscription with Contributor access
- Azure Container Registry (ACR) or Docker Hub account
- Azure Kubernetes Service (AKS) cluster
- Azure Cache for Redis (required for Spring Session)
- Application Gateway Ingress Controller (AGIC) enabled on AKS

---

## Project Structure

```
RLMono/
├── Dockerfile                    # Multi-stage Docker build
├── docker-compose.yml            # Local development compose file
├── .dockerignore                 # Docker build exclusions
├── pom.xml                       # Maven build descriptor
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
│   ├── build-push.sh             # Linux/macOS build & push
│   ├── build-push.bat            # Windows build & push
│   ├── deploy-image.sh           # Linux/macOS AKS deploy
│   └── deploy-image.bat          # Windows AKS deploy
└── docs/
    └── DEPLOYMENT.md             # This file
```

---

## Local Development with Docker Compose

### 1. Configure Environment Variables

Create a `.env` file in the project root (never commit this file):

```bash
# Redis connection (use a local Redis or Azure Cache for Redis)
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_SSL=false

# External service endpoints
APP_PAYMENT_ENDPOINT=http://payment-svc:9090/charge
APP_INVENTORY_ENDPOINT=http://inventory-svc:8081/rooms
APP_NOTIFICATION_ENDPOINT=http://notify-svc:7070/send
```

### 2. Start the Application

```bash
# Build and start
docker-compose up --build

# Start in background
docker-compose up -d --build

# View logs
docker-compose logs -f resortslite

# Stop
docker-compose down
```

### 3. Verify the Application

```bash
# Health check
curl http://localhost:8080/actuator/health

# Test booking endpoint
curl -X POST "http://localhost:8080/api/bookings/create?guestName=John&roomType=SUITE&checkIn=2024-06-01&checkOut=2024-06-05"

# Check availability
curl "http://localhost:8080/api/bookings/availability?roomType=DELUXE"
```

---

## Build and Push Docker Image

### Linux / macOS

```bash
# Make script executable
chmod +x scripts/build-push.sh

# Run from repository root
./scripts/build-push.sh
```

The script will prompt for:
1. Image tag (default: `latest`)
2. Registry type: `1` for Azure ACR, `2` for Docker Hub
3. Registry credentials

### Windows

```cmd
REM Run from repository root
scripts\build-push.bat
```

### Manual Build (Advanced)

```bash
# Build image
docker build -f Dockerfile -t resortslite:latest .

# Tag for ACR
docker tag resortslite:latest <acr-name>.azurecr.io/resortslite:latest

# Login to ACR
az acr login --name <acr-name>

# Push
docker push <acr-name>.azurecr.io/resortslite:latest
```

---

## Azure AKS Deployment

### Step 1: Set Up Azure CLI

```bash
# Login to Azure
az login

# Set subscription
az account set --subscription "<subscription-id>"
```

### Step 2: Create Azure Container Registry (if not existing)

```bash
az acr create \
  --resource-group <resource-group> \
  --name <acr-name> \
  --sku Basic

# Attach ACR to AKS (grants pull permissions)
az aks update \
  --resource-group <resource-group> \
  --name <aks-cluster> \
  --attach-acr <acr-name>
```

### Step 3: Create AKS Cluster (if not existing)

```bash
az aks create \
  --resource-group <resource-group> \
  --name <aks-cluster> \
  --node-count 2 \
  --node-vm-size Standard_DS2_v2 \
  --enable-addons ingress-appgw \
  --appgw-name resortslite-agw \
  --appgw-subnet-cidr "10.225.0.0/16" \
  --generate-ssh-keys
```

### Step 4: Create Azure Cache for Redis

```bash
az redis create \
  --resource-group <resource-group> \
  --name <redis-name> \
  --location <location> \
  --sku Basic \
  --vm-size c0

# Get connection details
az redis show --resource-group <resource-group> --name <redis-name> --query hostName
az redis list-keys --resource-group <resource-group> --name <redis-name> --query primaryKey
```

### Step 5: Build and Push Image

```bash
./scripts/build-push.sh
# Select ACR, enter your ACR name, tag as desired
```

### Step 6: Deploy to AKS

#### Linux / macOS
```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

#### Windows
```cmd
scripts\deploy-image.bat
```

The deploy script will prompt for:
- Azure Resource Group
- AKS Cluster name
- Full Docker image URI (e.g., `myregistry.azurecr.io/resortslite:1.0.0`)
- Redis connection details (host, port, password, SSL)
- External service endpoints

### Step 7: Verify Deployment

```bash
# Check pods
kubectl get pods -n resortslite

# Check services
kubectl get svc -n resortslite

# Check ingress (wait for IP assignment)
kubectl get ingress -n resortslite

# View pod logs
kubectl logs -l app=resortslite -n resortslite --tail=100

# Health check via port-forward
kubectl port-forward svc/resortslite-service 8080:80 -n resortslite
curl http://localhost:8080/actuator/health
```

---

## Kubernetes Manifest Reference

### namespace.yaml
Creates the `resortslite` namespace to isolate all application resources.

### deployment.yaml
- **Replicas**: 2 (horizontal scaling)
- **Image**: Pulled from `{{IMAGE_URI}}` (replaced at deploy time)
- **Resources**: 250m CPU / 512Mi memory (requests); 500m CPU / 1Gi memory (limits)
- **Probes**: Both liveness and readiness use `/actuator/health` on port 8080
  - Liveness: initial delay 60s (JVM startup), period 30s
  - Readiness: initial delay 30s, period 15s
- **JVM Options**: `-Xms256m -Xmx512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0`

### service.yaml
- **Type**: ClusterIP (internal cluster access only)
- **Port mapping**: 80 → 8080 (container port)

### ingress.yaml
- **Controller**: Azure Application Gateway Ingress Controller (AGIC)
- **Host**: `resortslite.example.com` (update to your actual domain)
- **Path**: `/` (all traffic routed to the application)

---

## Configuration and Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Spring profile | `docker` |
| `REDIS_HOST` | Azure Cache for Redis hostname | `localhost` |
| `REDIS_PORT` | Redis port | `6379` |
| `REDIS_PASSWORD` | Redis access key | _(empty)_ |
| `REDIS_SSL` | Enable TLS for Redis | `false` |
| `APP_PAYMENT_ENDPOINT` | Payment service URL | `http://payment-svc:9090/charge` |
| `APP_INVENTORY_ENDPOINT` | Inventory service URL | `http://inventory-svc:8081/rooms` |
| `APP_NOTIFICATION_ENDPOINT` | Notification service URL | `http://notify-svc:7070/send` |
| `JAVA_OPTS` | JVM startup options | `-Xms256m -Xmx512m ...` |
| `TZ` | Container timezone | `UTC` |

### Using Kubernetes Secrets for Sensitive Values

```bash
# Create secret for Redis password
kubectl create secret generic resortslite-secrets \
  --from-literal=redis-password=<your-redis-key> \
  -n resortslite
```

Then reference in `deployment.yaml`:
```yaml
- name: REDIS_PASSWORD
  valueFrom:
    secretKeyRef:
      name: resortslite-secrets
      key: redis-password
```

---

## Health Checks and Monitoring

### Spring Boot Actuator Endpoints

| Endpoint | URL | Purpose |
|----------|-----|---------|
| Health | `GET /actuator/health` | Liveness & readiness probe |
| Info | `GET /actuator/info` | Application metadata |

### Kubernetes Probes

The deployment configures:
- **Liveness Probe**: `/actuator/health` — restarts container if unhealthy
- **Readiness Probe**: `/actuator/health` — removes pod from load balancer if not ready

### JVM Monitoring

For production monitoring, consider adding:
```yaml
- name: JAVA_OPTS
  value: "-Xms256m -Xmx512m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dcom.sun.management.jmxremote"
```

---

## Scaling and Management

### Manual Scaling

```bash
# Scale to 3 replicas
kubectl scale deployment resortslite --replicas=3 -n resortslite

# Check rollout status
kubectl rollout status deployment/resortslite -n resortslite
```

### Horizontal Pod Autoscaler (HPA)

```bash
kubectl autoscale deployment resortslite \
  --cpu-percent=70 \
  --min=2 \
  --max=10 \
  -n resortslite

kubectl get hpa -n resortslite
```

### Rolling Update

```bash
# Update image
kubectl set image deployment/resortslite \
  resortslite=<acr-name>.azurecr.io/resortslite:<new-tag> \
  -n resortslite

# Monitor rollout
kubectl rollout status deployment/resortslite -n resortslite
```

---

## Troubleshooting

### Pod Not Starting

```bash
# Describe pod for events
kubectl describe pod -l app=resortslite -n resortslite

# Check logs
kubectl logs -l app=resortslite -n resortslite --previous
```

**Common causes:**
- `ImagePullBackOff`: ACR not attached to AKS, or wrong image URI
- `CrashLoopBackOff`: Application startup failure — check logs for Redis connection errors
- `OOMKilled`: Increase memory limits in `deployment.yaml`

### Redis Connection Failure

```bash
# Verify Redis env vars in running pod
kubectl exec -it <pod-name> -n resortslite -- env | grep REDIS

# Test Redis connectivity from pod
kubectl exec -it <pod-name> -n resortslite -- sh -c "nc -zv $REDIS_HOST $REDIS_PORT"
```

**Common causes:**
- Wrong `REDIS_HOST` — use the full Azure Cache for Redis hostname (`.redis.cache.windows.net`)
- `REDIS_SSL=false` when Azure Redis requires TLS — set `REDIS_SSL=true` and `REDIS_PORT=6380`
- Missing firewall rule — add AKS subnet to Redis firewall

### Ingress Not Accessible

```bash
# Check ingress status
kubectl describe ingress resortslite-ingress -n resortslite

# Check AGIC logs
kubectl logs -l app=ingress-appgw -n kube-system --tail=50
```

**Common causes:**
- Application Gateway not provisioned — wait 5-10 minutes after cluster creation
- DNS not pointing to ingress IP — update DNS A record

### Health Check Failing

```bash
# Port-forward and test manually
kubectl port-forward svc/resortslite-service 8080:80 -n resortslite
curl -v http://localhost:8080/actuator/health
```

**Common causes:**
- JVM startup time exceeds `initialDelaySeconds` — increase to 90s for slow starts
- Redis health check failing — verify Redis connectivity

---

## Security Considerations

1. **Never commit secrets** — use Kubernetes Secrets or Azure Key Vault
2. **Use Azure Key Vault** with AKS Secrets Store CSI Driver for production secrets
3. **Enable Redis TLS** — set `REDIS_SSL=true` and `REDIS_PORT=6380` for Azure Cache for Redis
4. **Network Policies** — restrict pod-to-pod communication in production
5. **Image scanning** — enable ACR vulnerability scanning
6. **Non-root container** — the Dockerfile runs as `appuser` (non-root)
7. **Resource limits** — always set CPU/memory limits to prevent resource exhaustion
8. **RBAC** — use least-privilege service accounts for AKS workloads

---

## Rollback Procedure

```bash
# Immediate rollback to previous version
kubectl rollout undo deployment/resortslite -n resortslite

# Rollback to specific revision
kubectl rollout history deployment/resortslite -n resortslite
kubectl rollout undo deployment/resortslite --to-revision=<revision-number> -n resortslite

# Verify rollback
kubectl rollout status deployment/resortslite -n resortslite
kubectl get pods -n resortslite
```

---

## Java-Specific Notes

- **Java 8 / Spring Boot 2.7.x**: Uses `eclipse-temurin:8-jre` runtime image (explicit base image provided)
- **JVM Container Support**: `-XX:+UseContainerSupport` ensures JVM respects container memory limits
- **MaxRAMPercentage**: Set to 75% to leave headroom for OS and non-heap memory
- **Spring Session Redis**: Application uses Redis-backed sessions for horizontal scaling — ensure Redis is available before pods start
- **H2 In-Memory Database**: The application uses H2 for persistence — data is not persisted across pod restarts. For production, migrate to Azure Database for PostgreSQL or Azure SQL
- **Actuator Health**: The `/actuator/health` endpoint includes Redis health status — if Redis is unavailable, the pod will be marked unhealthy
- **Graceful Shutdown**: `terminationGracePeriodSeconds: 30` allows in-flight requests to complete before pod termination
