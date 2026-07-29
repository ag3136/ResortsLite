# ResortsLite - Deployment Guide

## Table of Contents
1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Local Development Setup](#local-development-setup)
4. [Docker Deployment](#docker-deployment)
5. [AWS EKS Deployment](#aws-eks-deployment)
6. [Configuration Management](#configuration-management)
7. [Troubleshooting](#troubleshooting)
8. [Security Considerations](#security-considerations)
9. [Monitoring and Observability](#monitoring-and-observability)

---

## Overview

ResortsLite is a Spring Boot 2.7.x application built with Java 8, designed for containerized deployment on AWS EKS (Elastic Kubernetes Service). This guide provides comprehensive instructions for building, deploying, and managing the application in various environments.

### Technology Stack
- **Framework**: Spring Boot 2.7.18
- **Java Version**: Java 8 (1.8)
- **Build Tool**: Maven 3.x
- **Database**: H2 (in-memory, configurable for external databases)
- **Container Runtime**: Docker
- **Orchestration**: Kubernetes (AWS EKS)
- **Base Image**: Amazon Corretto 8

### Application Details
- **Application Port**: 8080
- **Health Check Endpoint**: `/actuator/health`
- **Info Endpoint**: `/actuator/info`
- **Management Endpoints**: Exposed via Spring Boot Actuator

---

## Prerequisites

### Required Tools

#### For Local Development
- **Java Development Kit (JDK) 8**: [Download OpenJDK 8](https://adoptium.net/)
- **Maven 3.6+**: [Download Maven](https://maven.apache.org/download.cgi)
- **Docker Desktop**: [Download Docker](https://www.docker.com/products/docker-desktop)
- **Docker Compose**: Included with Docker Desktop

#### For AWS EKS Deployment
- **AWS CLI v2**: [Installation Guide](https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html)
- **kubectl**: [Installation Guide](https://kubernetes.io/docs/tasks/tools/)
- **eksctl** (optional): [Installation Guide](https://eksctl.io/introduction/#installation)

### AWS Prerequisites
- **AWS Account** with appropriate permissions
- **IAM User/Role** with the following permissions:
  - ECR: `ecr:*` (for container registry)
  - EKS: `eks:*` (for cluster management)
  - EC2: `ec2:DescribeInstances`, `ec2:DescribeSecurityGroups`, etc.
  - IAM: `iam:CreateRole`, `iam:AttachRolePolicy` (for service roles)
- **EKS Cluster** (existing or new)
- **AWS Load Balancer Controller** installed on EKS cluster

### Verify Prerequisites

```bash
# Check Java version
java -version

# Check Maven version
mvn -version

# Check Docker version
docker --version

# Check AWS CLI version
aws --version

# Check kubectl version
kubectl version --client

# Verify AWS credentials
aws sts get-caller-identity
```

---

## Local Development Setup

### 1. Clone the Repository

```bash
git clone <repository-url>
cd ResortsLiteMono
```

### 2. Build the Application

```bash
# Clean and build the application
mvn clean package

# Skip tests for faster builds
mvn clean package -DskipTests
```

The compiled JAR will be located at `target/resortsLite-1.0.0.jar`.

### 3. Run Locally (Without Docker)

```bash
# Run the application
java -jar target/resortsLite-1.0.0.jar

# Or use Maven Spring Boot plugin
mvn spring-boot:run
```

Access the application:
- **Application**: http://localhost:8080
- **Health Check**: http://localhost:8080/actuator/health
- **H2 Console**: http://localhost:8080/h2-console

### 4. Run with Docker Compose

```bash
# Build and start the application
docker-compose up --build

# Run in detached mode
docker-compose up -d

# View logs
docker-compose logs -f

# Stop the application
docker-compose down
```

---

## Docker Deployment

### Build Docker Image Manually

```bash
# Build the Docker image
docker build -t resortslite:latest .

# Run the container
docker run -d \
  --name resortslite \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=docker \
  resortslite:latest

# View logs
docker logs -f resortslite

# Stop and remove container
docker stop resortslite
docker rm resortslite
```

### Build and Push to Registry

Use the provided scripts to build and push images to AWS ECR or Docker Hub.

#### Linux/macOS

```bash
# Make script executable
chmod +x scripts/build-push.sh

# Run the script
./scripts/build-push.sh
```

#### Windows

```cmd
# Run the script
scripts\build-push.bat
```

The script will:
1. Prompt for registry type (AWS ECR or Docker Hub)
2. Request registry credentials and details
3. Build the Docker image
4. Authenticate with the selected registry
5. Push the image to the registry

**Example for AWS ECR:**
- AWS Region: `us-east-1`
- AWS Account ID: `123456789012`
- ECR Repository: `resortslite`
- Image Tag: `v1.0.0`

**Example for Docker Hub:**
- Username: `myusername`
- Password: `********`
- Image Tag: `v1.0.0`

---

## AWS EKS Deployment

### Prerequisites

1. **Create EKS Cluster** (if not exists)

```bash
# Using eksctl (recommended)
eksctl create cluster \
  --name resortslite-cluster \
  --region us-east-1 \
  --nodegroup-name standard-workers \
  --node-type t3.medium \
  --nodes 2 \
  --nodes-min 1 \
  --nodes-max 3 \
  --managed

# Or use AWS Console/CloudFormation
```

2. **Install AWS Load Balancer Controller**

```bash
# Add IAM policy for Load Balancer Controller
curl -o iam_policy.json https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/v2.4.7/docs/install/iam_policy.json

aws iam create-policy \
  --policy-name AWSLoadBalancerControllerIAMPolicy \
  --policy-document file://iam_policy.json

# Install using Helm
helm repo add eks https://aws.github.io/eks-charts
helm repo update

helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=resortslite-cluster \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller
```

### Deploy to EKS

#### Linux/macOS

```bash
# Make script executable
chmod +x scripts/deploy-image.sh

# Run the deployment script
./scripts/deploy-image.sh
```

#### Windows

```cmd
# Run the deployment script
scripts\deploy-image.bat
```

The script will:
1. Prompt for AWS region and EKS cluster name
2. Request Docker image URI
3. Prompt for environment variables (database, external services)
4. Configure kubectl to connect to EKS
5. Update Kubernetes manifests with provided values
6. Deploy namespace, deployment, service, and ingress
7. Wait for deployment rollout
8. Display deployment status and access URLs

**Example Inputs:**
- AWS Region: `us-east-1`
- EKS Cluster Name: `resortslite-cluster`
- Image URI: `123456789012.dkr.ecr.us-east-1.amazonaws.com/resortslite:v1.0.0`
- Database URL: `jdbc:postgresql://mydb.us-east-1.rds.amazonaws.com:5432/resortdb`
- Database Username: `admin`
- Database Password: `********`

### Manual Deployment Steps

If you prefer manual deployment:

```bash
# Configure kubectl
aws eks update-kubeconfig --region us-east-1 --name resortslite-cluster

# Update deployment.yaml with your image URI
sed -i 's|{{IMAGE_URI}}|123456789012.dkr.ecr.us-east-1.amazonaws.com/resortslite:v1.0.0|g' kubernetes/deployment.yaml

# Update environment variables in deployment.yaml
# Edit kubernetes/deployment.yaml and replace {{PLACEHOLDER}} values

# Apply manifests
kubectl apply -f kubernetes/namespace.yaml
kubectl apply -f kubernetes/deployment.yaml
kubectl apply -f kubernetes/service.yaml
kubectl apply -f kubernetes/ingress.yaml

# Check deployment status
kubectl rollout status deployment/resortslite -n resortslite

# View resources
kubectl get all -n resortslite
kubectl get ingress -n resortslite
```

### Access the Application

```bash
# Get the Load Balancer URL
kubectl get ingress resortslite-ingress -n resortslite

# The output will show the ALB hostname
# Example: k8s-resortsl-resortsl-abc123-1234567890.us-east-1.elb.amazonaws.com

# Access the application
curl http://<ALB-HOSTNAME>/actuator/health
```

---

## Configuration Management

### Environment Variables

The application uses the following environment variables:

| Variable | Description | Default Value |
|----------|-------------|---------------|
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `docker` |
| `SERVER_PORT` | Application port | `8080` |
| `JAVA_OPTS` | JVM options | `-Xmx512m -Xms256m` |
| `SPRING_DATASOURCE_URL` | Database connection URL | `jdbc:h2:mem:resortdb` |
| `SPRING_DATASOURCE_USERNAME` | Database username | `sa` |
| `SPRING_DATASOURCE_PASSWORD` | Database password | (empty) |
| `APP_PAYMENT_ENDPOINT` | Payment service endpoint | `http://payment-svc:9090/charge` |
| `APP_INVENTORY_ENDPOINT` | Inventory service endpoint | `http://inventory-svc:8081/rooms` |
| `APP_NOTIFICATION_ENDPOINT` | Notification service endpoint | `http://notify-svc:7070/send` |

### Kubernetes ConfigMaps and Secrets

For production deployments, use ConfigMaps and Secrets:

```yaml
# configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: resortslite-config
  namespace: resortslite
data:
  SPRING_PROFILES_ACTIVE: "production"
  SERVER_PORT: "8080"
  APP_PAYMENT_ENDPOINT: "http://payment-svc:9090/charge"
  APP_INVENTORY_ENDPOINT: "http://inventory-svc:8081/rooms"
  APP_NOTIFICATION_ENDPOINT: "http://notify-svc:7070/send"

---
# secret.yaml
apiVersion: v1
kind: Secret
metadata:
  name: resortslite-secret
  namespace: resortslite
type: Opaque
stringData:
  SPRING_DATASOURCE_URL: "jdbc:postgresql://mydb.us-east-1.rds.amazonaws.com:5432/resortdb"
  SPRING_DATASOURCE_USERNAME: "admin"
  SPRING_DATASOURCE_PASSWORD: "mypassword"
```

Apply the ConfigMap and Secret:

```bash
kubectl apply -f configmap.yaml
kubectl apply -f secret.yaml
```

Update `deployment.yaml` to reference them:

```yaml
envFrom:
- configMapRef:
    name: resortslite-config
- secretRef:
    name: resortslite-secret
```

### External Database Configuration

To use an external database (e.g., AWS RDS):

1. **Create RDS Instance** (PostgreSQL, MySQL, etc.)
2. **Update Security Groups** to allow EKS worker nodes
3. **Set Environment Variables**:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://mydb.us-east-1.rds.amazonaws.com:5432/resortdb
SPRING_DATASOURCE_USERNAME=admin
SPRING_DATASOURCE_PASSWORD=mypassword
```

4. **Update pom.xml** with appropriate JDBC driver:

```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

---

## Troubleshooting

### Common Issues

#### 1. Pod Not Starting

```bash
# Check pod status
kubectl get pods -n resortslite

# Describe pod for events
kubectl describe pod <pod-name> -n resortslite

# View pod logs
kubectl logs <pod-name> -n resortslite

# View previous logs (if pod restarted)
kubectl logs <pod-name> -n resortslite --previous
```

**Common Causes:**
- Image pull errors (check ECR permissions)
- Insufficient resources (check node capacity)
- Configuration errors (check environment variables)
- Health check failures (check application startup)

#### 2. Image Pull Errors

```bash
# Check if ECR repository exists
aws ecr describe-repositories --repository-names resortslite --region us-east-1

# Verify image exists
aws ecr describe-images --repository-name resortslite --region us-east-1

# Check EKS node IAM role has ECR permissions
aws iam list-attached-role-policies --role-name <eks-node-role>
```

**Solution:**
- Ensure ECR repository exists
- Verify image tag is correct
- Check EKS node IAM role has `AmazonEC2ContainerRegistryReadOnly` policy

#### 3. Service Not Accessible

```bash
# Check service endpoints
kubectl get endpoints -n resortslite

# Check service details
kubectl describe service resortslite-service -n resortslite

# Test service from within cluster
kubectl run -it --rm debug --image=busybox --restart=Never -- wget -O- http://resortslite-service.resortslite.svc.cluster.local/actuator/health
```

#### 4. Ingress Not Working

```bash
# Check ingress status
kubectl describe ingress resortslite-ingress -n resortslite

# Check AWS Load Balancer Controller logs
kubectl logs -n kube-system -l app.kubernetes.io/name=aws-load-balancer-controller

# Verify ALB created
aws elbv2 describe-load-balancers --region us-east-1
```

**Common Causes:**
- AWS Load Balancer Controller not installed
- Incorrect ingress annotations
- Security group rules blocking traffic
- Subnet tags missing for ALB

#### 5. Health Check Failures

```bash
# Check liveness probe
kubectl describe pod <pod-name> -n resortslite | grep -A 10 "Liveness"

# Test health endpoint manually
kubectl exec -it <pod-name> -n resortslite -- curl http://localhost:8080/actuator/health
```

**Solution:**
- Increase `initialDelaySeconds` for slow startup
- Verify health endpoint is accessible
- Check application logs for errors

### Debugging Commands

```bash
# Get all resources in namespace
kubectl get all -n resortslite

# View events
kubectl get events -n resortslite --sort-by='.lastTimestamp'

# Execute commands in pod
kubectl exec -it <pod-name> -n resortslite -- /bin/sh

# Port forward for local testing
kubectl port-forward -n resortslite service/resortslite-service 8080:80

# View resource usage
kubectl top pods -n resortslite
kubectl top nodes
```

---

## Security Considerations

### Container Security

1. **Non-Root User**: The Dockerfile creates and uses a non-root user (`appuser`)
2. **Minimal Base Image**: Uses Amazon Corretto 8 (optimized and secure)
3. **No Unnecessary Tools**: Runtime image contains only JRE and application

### Kubernetes Security

1. **Network Policies**: Implement network policies to restrict pod communication

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
      port: 443
    - protocol: TCP
      port: 5432
```

2. **Pod Security Standards**: Apply pod security policies

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: resortslite
spec:
  securityContext:
    runAsNonRoot: true
    runAsUser: 1000
    fsGroup: 1000
  containers:
  - name: resortslite
    securityContext:
      allowPrivilegeEscalation: false
      readOnlyRootFilesystem: true
      capabilities:
        drop:
        - ALL
```

3. **Secrets Management**: Use AWS Secrets Manager or Parameter Store

```bash
# Install External Secrets Operator
helm repo add external-secrets https://charts.external-secrets.io
helm install external-secrets external-secrets/external-secrets -n external-secrets-system --create-namespace
```

### AWS Security

1. **IAM Roles**: Use IRSA (IAM Roles for Service Accounts)
2. **VPC Configuration**: Deploy EKS in private subnets
3. **Security Groups**: Restrict inbound/outbound traffic
4. **Encryption**: Enable encryption at rest for EBS volumes and RDS

---

## Monitoring and Observability

### Spring Boot Actuator Endpoints

The application exposes the following actuator endpoints:

- `/actuator/health` - Health status
- `/actuator/info` - Application information
- `/actuator/metrics` - Application metrics

### Prometheus Integration

Add Prometheus annotations to the deployment:

```yaml
metadata:
  annotations:
    prometheus.io/scrape: "true"
    prometheus.io/port: "8080"
    prometheus.io/path: "/actuator/prometheus"
```

Add Micrometer Prometheus dependency to `pom.xml`:

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

### CloudWatch Container Insights

Enable Container Insights for EKS:

```bash
# Install CloudWatch agent
kubectl apply -f https://raw.githubusercontent.com/aws-samples/amazon-cloudwatch-container-insights/latest/k8s-deployment-manifest-templates/deployment-mode/daemonset/container-insights-monitoring/quickstart/cwagent-fluentd-quickstart.yaml
```

### Logging

Configure centralized logging with Fluentd or Fluent Bit:

```bash
# Install Fluent Bit
kubectl apply -f https://raw.githubusercontent.com/fluent/fluent-bit-kubernetes-logging/master/fluent-bit-service-account.yaml
kubectl apply -f https://raw.githubusercontent.com/fluent/fluent-bit-kubernetes-logging/master/fluent-bit-role.yaml
kubectl apply -f https://raw.githubusercontent.com/fluent/fluent-bit-kubernetes-logging/master/fluent-bit-role-binding.yaml
kubectl apply -f https://raw.githubusercontent.com/fluent/fluent-bit-kubernetes-logging/master/output/elasticsearch/fluent-bit-configmap.yaml
kubectl apply -f https://raw.githubusercontent.com/fluent/fluent-bit-kubernetes-logging/master/output/elasticsearch/fluent-bit-ds.yaml
```

### Application Performance Monitoring (APM)

Integrate with APM tools:

- **AWS X-Ray**: Add X-Ray SDK to trace requests
- **Datadog**: Install Datadog agent as DaemonSet
- **New Relic**: Add New Relic Java agent

---

## Scaling and High Availability

### Horizontal Pod Autoscaler (HPA)

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

Apply HPA:

```bash
kubectl apply -f hpa.yaml
kubectl get hpa -n resortslite
```

### Cluster Autoscaler

Enable cluster autoscaler for EKS:

```bash
# Install cluster autoscaler
kubectl apply -f https://raw.githubusercontent.com/kubernetes/autoscaler/master/cluster-autoscaler/cloudprovider/aws/examples/cluster-autoscaler-autodiscover.yaml

# Update cluster autoscaler deployment with cluster name
kubectl -n kube-system edit deployment cluster-autoscaler
```

---

## Rollback and Updates

### Rolling Updates

```bash
# Update image
kubectl set image deployment/resortslite resortslite=<new-image-uri> -n resortslite

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
```

---

## Cleanup

### Delete Kubernetes Resources

```bash
# Delete all resources in namespace
kubectl delete namespace resortslite

# Or delete individual resources
kubectl delete -f kubernetes/
```

### Delete EKS Cluster

```bash
# Using eksctl
eksctl delete cluster --name resortslite-cluster --region us-east-1

# Or use AWS Console/CloudFormation
```

### Delete ECR Repository

```bash
# Delete ECR repository
aws ecr delete-repository --repository-name resortslite --region us-east-1 --force
```

---

## Additional Resources

- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/2.7.x/reference/html/)
- [AWS EKS Documentation](https://docs.aws.amazon.com/eks/)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Docker Documentation](https://docs.docker.com/)
- [AWS Load Balancer Controller](https://kubernetes-sigs.github.io/aws-load-balancer-controller/)

---

## Support and Contact

For issues or questions:
- Check the [Troubleshooting](#troubleshooting) section
- Review application logs: `kubectl logs -n resortslite -l app=resortslite`
- Contact the development team

---

**Last Updated**: 2024
**Version**: 1.0.0
