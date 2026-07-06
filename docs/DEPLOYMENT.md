# ResortsLite — AWS ECS Fargate Deployment Guide

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Local Development with Docker Compose](#local-development-with-docker-compose)
4. [Build and Push Docker Image](#build-and-push-docker-image)
5. [AWS ECS Fargate Prerequisites](#aws-ecs-fargate-prerequisites)
6. [ECS Task Definition Explained](#ecs-task-definition-explained)
7. [ECS Service Configuration](#ecs-service-configuration)
8. [ECS Fargate Deployment Walkthrough](#ecs-fargate-deployment-walkthrough)
9. [ECS-Specific Troubleshooting](#ecs-specific-troubleshooting)
10. [ECS Fargate Scaling and Management](#ecs-fargate-scaling-and-management)
11. [Configuration Management](#configuration-management)
12. [Security Considerations](#security-considerations)
13. [Java-Specific Notes](#java-specific-notes)

---

## Overview

**Application**: ResortsLite  
**Framework**: Spring Boot 2.7.18  
**Java Version**: 8 (eclipse-temurin:8-jdk-alpine)  
**Build Tool**: Maven  
**Application Port**: 8080  
**Health Endpoint**: `/actuator/health`  
**Target Platform**: AWS ECS Fargate  

ResortsLite is a Spring Boot REST API for resort booking management. It uses:
- **Amazon S3** for report and backup storage
- **Amazon ElastiCache (Redis)** for distributed session management
- **Spring Boot Actuator** for health and info endpoints
- **H2 in-memory database** for local/demo use

---

## Prerequisites

### Local Development
- Docker Desktop 24.x or later
- Docker Compose v2.x or later
- Java 8 JDK (for local builds outside Docker)
- Maven 3.9.x (for local builds outside Docker)

### AWS Deployment
- AWS CLI v2 configured (`aws configure`)
- IAM permissions for: ECS, ECR, CloudWatch Logs, ELBv2, IAM
- An existing VPC with at least 2 subnets in different AZs
- Security group allowing inbound TCP on port 8080 (and 80 if using ALB)
- Amazon ElastiCache Redis cluster (or endpoint)
- Amazon S3 buckets for reports and backups

---

## Local Development with Docker Compose

### 1. Configure environment variables

Create a `.env` file in the project root:

```env
REDIS_HOST=your-elasticache-endpoint.cache.amazonaws.com
REDIS_PORT=6379
S3_REPORT_BUCKET=your-reports-bucket
S3_BACKUP_BUCKET=your-backup-bucket
AWS_REGION=us-east-1
PAYMENT_API_URL=http://payment-service/payments/charge
REPORT_SERVICE_URL=http://report-service/api/reports
```

> **Note**: For local testing without Redis, you can run a Redis container separately:
> ```bash
> docker run -d -p 6379:6379 redis:7-alpine
> ```
> Then set `REDIS_HOST=localhost` in your `.env`.

### 2. Build and start the application

```bash
# Build and start
docker compose up --build

# Run in background
docker compose up --build -d

# View logs
docker compose logs -f resortslite

# Stop
docker compose down
```

### 3. Verify the application

```bash
# Health check
curl http://localhost:8080/actuator/health

# Application info
curl http://localhost:8080/actuator/info

# Create a booking
curl -X POST "http://localhost:8080/api/bookings/create?guestName=John&roomType=SUITE&checkIn=2024-06-01&checkOut=2024-06-05"
```

---

## Build and Push Docker Image

### Linux/macOS

```bash
# Make the script executable
chmod +x scripts/build-push.sh

# Run from repository root
./scripts/build-push.sh
```

The script will prompt you to:
1. Enter an image tag (default: `latest`)
2. Select registry: **1. AWS ECR** or **2. Docker Hub**
3. Provide registry-specific credentials and details

**ECR example flow:**
```
Enter image tag [latest]: v1.0.0
Select container registry:
  1. AWS ECR
  2. Docker Hub
Enter choice [1]: 1
Enter AWS region [us-east-1]: us-east-1
Enter AWS Account ID (leave blank to auto-detect): 
Enter ECR repository name [resortslite]: resortslite
```

### Windows

```cmd
scripts\build-push.bat
```

---

## AWS ECS Fargate Prerequisites

### 1. IAM Roles

#### ECS Task Execution Role
This role allows ECS to pull images from ECR and write logs to CloudWatch.

```bash
# Create the role (if it doesn't exist)
aws iam create-role \
  --role-name ecsTaskExecutionRole \
  --assume-role-policy-document '{
    "Version": "2012-10-17",
    "Statement": [{
      "Effect": "Allow",
      "Principal": {"Service": "ecs-tasks.amazonaws.com"},
      "Action": "sts:AssumeRole"
    }]
  }'

# Attach the managed policy
aws iam attach-role-policy \
  --role-name ecsTaskExecutionRole \
  --policy-arn arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy
```

#### ECS Task Role (for S3 and other AWS service access)
```bash
aws iam create-role \
  --role-name ecsTaskRole \
  --assume-role-policy-document '{
    "Version": "2012-10-17",
    "Statement": [{
      "Effect": "Allow",
      "Principal": {"Service": "ecs-tasks.amazonaws.com"},
      "Action": "sts:AssumeRole"
    }]
  }'

# Attach S3 access policy
aws iam attach-role-policy \
  --role-name ecsTaskRole \
  --policy-arn arn:aws:iam::aws:policy/AmazonS3FullAccess
```

### 2. Security Group

Create a security group that allows inbound traffic on port 8080:

```bash
aws ec2 create-security-group \
  --group-name resortslite-sg \
  --description "ResortsLite ECS security group" \
  --vpc-id vpc-xxxxxxxx

# Allow inbound on port 8080
aws ec2 authorize-security-group-ingress \
  --group-id sg-xxxxxxxx \
  --protocol tcp \
  --port 8080 \
  --cidr 0.0.0.0/0

# Allow inbound on port 80 (if using ALB)
aws ec2 authorize-security-group-ingress \
  --group-id sg-xxxxxxxx \
  --protocol tcp \
  --port 80 \
  --cidr 0.0.0.0/0
```

### 3. CloudWatch Log Group

```bash
aws logs create-log-group --log-group-name /ecs/resortslite --region us-east-1
```

### 4. Amazon ElastiCache Redis

Create a Redis cluster for distributed session management:

```bash
aws elasticache create-cache-cluster \
  --cache-cluster-id resortslite-redis \
  --cache-node-type cache.t3.micro \
  --engine redis \
  --num-cache-nodes 1 \
  --region us-east-1
```

Note the endpoint and use it as `REDIS_HOST` in the task definition.

### 5. Amazon S3 Buckets

```bash
aws s3 mb s3://resorts-reports-bucket --region us-east-1
aws s3 mb s3://resorts-backup-bucket --region us-east-1
```

---

## ECS Task Definition Explained

The task definition (`ecs/task-definition.json`) configures how the container runs on Fargate:

| Field | Value | Description |
|-------|-------|-------------|
| `family` | `resortslite-task` | Task definition family name |
| `requiresCompatibilities` | `["FARGATE"]` | Fargate launch type |
| `networkMode` | `awsvpc` | Required for Fargate; each task gets its own ENI |
| `cpu` | `"512"` | 0.5 vCPU |
| `memory` | `"1024"` | 1 GB RAM |
| `executionRoleArn` | `ecsTaskExecutionRole` | Allows ECR pull and CloudWatch logging |
| `taskRoleArn` | `ecsTaskRole` | Allows S3 access from within the container |

### Valid Fargate CPU/Memory Combinations

| CPU | Valid Memory Values |
|-----|---------------------|
| 256 | 512, 1024, 2048 MB |
| **512** | **1024**, 2048, 3072, 4096 MB |
| 1024 | 2048–8192 MB |
| 2048 | 4096–16384 MB |
| 4096 | 8192–30720 MB |

### Container Definition Key Fields

- **`essential: true`** — ECS stops the task if this container exits
- **`portMappings`** — Only `containerPort` is needed (no `hostPort` for Fargate)
- **`logConfiguration`** — CloudWatch Logs via `awslogs` driver
- **`environment`** — Application-specific environment variables

---

## ECS Service Configuration

The service definition (`ecs/service-definition.json`) controls how tasks are managed:

| Field | Value | Description |
|-------|-------|-------------|
| `launchType` | `FARGATE` | Serverless container execution |
| `desiredCount` | `2` | Number of running task instances |
| `networkMode` | `awsvpc` | Each task gets its own private IP |
| `assignPublicIp` | `ENABLED` | Required for tasks in public subnets to pull images |
| `maximumPercent` | `200` | Allow up to 2x tasks during rolling deploy |
| `minimumHealthyPercent` | `50` | Keep at least 50% healthy during deploy |

---

## ECS Fargate Deployment Walkthrough

### Step 1: Build and push the Docker image

```bash
./scripts/build-push.sh
# Select ECR, enter your region and account details
# Note the full image URI output (e.g., 123456789.dkr.ecr.us-east-1.amazonaws.com/resortslite:latest)
```

### Step 2: Run the deployment script

```bash
chmod +x scripts/deploy-image.sh
./scripts/deploy-image.sh
```

You will be prompted for:
- AWS region
- ECS cluster name
- ECR image URI (from Step 1)
- Subnet IDs (at least 2 for HA)
- Security Group ID
- Redis host (ElastiCache endpoint)
- Whether to create an Application Load Balancer

### Step 3: Verify the deployment

```bash
# Check service status
aws ecs describe-services \
  --cluster resortslite-cluster \
  --services resortslite-service \
  --region us-east-1

# List running tasks
aws ecs list-tasks \
  --cluster resortslite-cluster \
  --service-name resortslite-service \
  --region us-east-1

# View application logs
aws logs tail /ecs/resortslite --follow --region us-east-1
```

### Step 4: Test the application

If using an ALB, the deployment script will print the DNS name:
```
http://resortslite-alb-xxxxxxxxxx.us-east-1.elb.amazonaws.com/actuator/health
```

If accessing tasks directly (no ALB), get the task's public IP:
```bash
TASK_ARN=$(aws ecs list-tasks --cluster resortslite-cluster --service-name resortslite-service --query "taskArns[0]" --output text)
ENI_ID=$(aws ecs describe-tasks --cluster resortslite-cluster --tasks $TASK_ARN --query "tasks[0].attachments[0].details[?name=='networkInterfaceId'].value" --output text)
PUBLIC_IP=$(aws ec2 describe-network-interfaces --network-interface-ids $ENI_ID --query "NetworkInterfaces[0].Association.PublicIp" --output text)
curl http://$PUBLIC_IP:8080/actuator/health
```

---

## ECS-Specific Troubleshooting

### Task fails to start

```bash
# Check stopped task reason
aws ecs describe-tasks \
  --cluster resortslite-cluster \
  --tasks <TASK_ARN> \
  --region us-east-1 \
  --query "tasks[0].{Status:lastStatus,StopReason:stoppedReason,Containers:containers[*].{Name:name,Reason:reason,ExitCode:exitCode}}"
```

**Common causes:**
- `CannotPullContainerError` → Check ECR permissions on `ecsTaskExecutionRole`, verify image URI
- `ResourceInitializationError` → Check VPC/subnet internet access for image pull
- `OutOfMemoryError` → Increase task memory in `task-definition.json`

### Application not healthy

```bash
# View recent logs
aws logs tail /ecs/resortslite --since 10m --region us-east-1

# Check if Redis is reachable
# Verify REDIS_HOST environment variable in task definition
```

**Common causes:**
- Redis connection refused → Verify `REDIS_HOST` and security group rules between ECS and ElastiCache
- S3 access denied → Verify `ecsTaskRole` has S3 permissions
- JVM OOM → Increase task memory or tune `JAVA_OPTS`

### Invalid CPU/memory combination

Ensure you use valid Fargate combinations. The default (`cpu: "512"`, `memory: "1024"`) is always valid.

### Network connectivity issues

- Ensure subnets have a route to the internet (via IGW or NAT Gateway) for ECR image pulls
- Ensure security groups allow outbound traffic on port 443 (for ECR/S3/CloudWatch)
- For ElastiCache: ensure security group allows inbound on port 6379 from ECS security group

### Service not stabilizing

```bash
# Check deployment events
aws ecs describe-services \
  --cluster resortslite-cluster \
  --services resortslite-service \
  --region us-east-1 \
  --query "services[0].events[:5]"
```

---

## ECS Fargate Scaling and Management

### Manual scaling

```bash
aws ecs update-service \
  --cluster resortslite-cluster \
  --service resortslite-service \
  --desired-count 4 \
  --region us-east-1
```

### Auto Scaling

```bash
# Register scalable target
aws application-autoscaling register-scalable-target \
  --service-namespace ecs \
  --resource-id service/resortslite-cluster/resortslite-service \
  --scalable-dimension ecs:service:DesiredCount \
  --min-capacity 2 \
  --max-capacity 10

# Create CPU-based scaling policy
aws application-autoscaling put-scaling-policy \
  --service-namespace ecs \
  --resource-id service/resortslite-cluster/resortslite-service \
  --scalable-dimension ecs:service:DesiredCount \
  --policy-name resortslite-cpu-scaling \
  --policy-type TargetTrackingScaling \
  --target-tracking-scaling-policy-configuration '{
    "TargetValue": 70.0,
    "PredefinedMetricSpecification": {
      "PredefinedMetricType": "ECSServiceAverageCPUUtilization"
    },
    "ScaleInCooldown": 300,
    "ScaleOutCooldown": 60
  }'
```

### Blue/Green Deployment with CodeDeploy

For zero-downtime deployments, configure CodeDeploy with ECS:

1. Create a CodeDeploy application and deployment group targeting the ECS service
2. Use `deploymentController: CODE_DEPLOY` in the service definition
3. Configure two target groups (blue and green) on the ALB
4. Trigger deployments via CodeDeploy with the new task definition ARN

### Rolling Update (default)

The current configuration uses rolling updates:
- `maximumPercent: 200` — Allows double the tasks during deployment
- `minimumHealthyPercent: 50` — Keeps at least 1 task running at all times

---

## Configuration Management

### Environment Variables Reference

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8080` | Application HTTP port |
| `SPRING_PROFILES_ACTIVE` | `docker` | Spring profile |
| `REDIS_HOST` | `localhost` | ElastiCache Redis endpoint |
| `REDIS_PORT` | `6379` | Redis port |
| `S3_REPORT_BUCKET` | `resorts-reports-bucket` | S3 bucket for reports |
| `S3_BACKUP_BUCKET` | `resorts-backup-bucket` | S3 bucket for backups |
| `AWS_REGION` | `us-east-1` | AWS region for SDK |
| `PAYMENT_API_URL` | `http://payment-service/payments/charge` | Payment service URL |
| `REPORT_SERVICE_URL` | `http://report-service/api/reports` | Report service URL |
| `JAVA_OPTS` | `-Xmx512m -Xms256m ...` | JVM options |
| `TZ` | `UTC` | Container timezone |

### Using AWS Secrets Manager for Sensitive Values

For production, store sensitive values in AWS Secrets Manager and reference them in the task definition:

```json
"secrets": [
  {
    "name": "REDIS_HOST",
    "valueFrom": "arn:aws:secretsmanager:us-east-1:123456789:secret:resortslite/redis-host"
  }
]
```

---

## Security Considerations

1. **Non-root container user**: The Dockerfile creates and uses `appuser` (non-root)
2. **IAM least privilege**: Scope `ecsTaskRole` to specific S3 buckets only
3. **Secrets management**: Use AWS Secrets Manager or SSM Parameter Store for sensitive values
4. **Network isolation**: Place ECS tasks in private subnets with NAT Gateway for production
5. **Security groups**: Restrict inbound to ALB security group only (not 0.0.0.0/0)
6. **ECR image scanning**: Enable ECR image scanning on push for vulnerability detection
7. **Log retention**: Set CloudWatch log retention policy (e.g., 30 days)
8. **Dependency vulnerabilities**: The pom.xml includes log4j 2.14.1 (CVE-2021-44228) and commons-collections 3.2.1 (CVE-2015-6420) — **upgrade these immediately in production**

```bash
# Set log retention
aws logs put-retention-policy \
  --log-group-name /ecs/resortslite \
  --retention-in-days 30 \
  --region us-east-1
```

---

## Java-Specific Notes

### JVM Memory Configuration

The container is configured with:
```
-Xmx512m          # Maximum heap: 512 MB
-Xms256m          # Initial heap: 256 MB
-XX:+UseContainerSupport    # JVM respects container memory limits
-XX:MaxRAMPercentage=75.0   # Use up to 75% of container RAM for heap
-Djava.security.egd=file:/dev/./urandom  # Faster SecureRandom for containers
```

With `memory: "1024"` (1 GB task), the JVM heap is capped at 512 MB, leaving ~512 MB for the JVM metaspace, thread stacks, and OS overhead.

### Spring Boot Actuator Endpoints

| Endpoint | URL | Purpose |
|----------|-----|---------|
| Health | `/actuator/health` | Liveness/readiness probe |
| Info | `/actuator/info` | Application metadata |

### Spring Session with Redis

The application uses Spring Session backed by Redis for distributed session management. Ensure the ElastiCache Redis cluster is accessible from ECS tasks before deployment.

### Startup Time

Spring Boot applications on Java 8 typically take 15–30 seconds to start. The ECS service health check grace period should be set to at least 60 seconds to avoid premature task termination.

### H2 Database

The application uses H2 in-memory database for demo purposes. In production, replace with Amazon RDS (PostgreSQL/MySQL) and update `spring.datasource.*` properties accordingly.

### Upgrading Vulnerable Dependencies

Before production deployment, upgrade the following in `pom.xml`:
- `log4j-core` from `2.14.1` → `2.17.2` or later (fixes CVE-2021-44228 Log4Shell)
- `commons-collections` from `3.2.1` → `3.2.2` or later (fixes CVE-2015-6420)
