#!/bin/bash
set -e
set -o pipefail

# ============================================
# Deploy to AWS EKS Script
# For ResortsLite Spring Boot Application
# ============================================

echo "=========================================="
echo "AWS EKS Deployment Script"
echo "=========================================="
echo ""

# Prompt for AWS configuration
read -p "Enter AWS Region (e.g., us-east-1): " AWS_REGION
if [ -z "$AWS_REGION" ]; then
    echo "ERROR: AWS Region is required"
    exit 1
fi

read -p "Enter EKS Cluster Name: " CLUSTER_NAME
if [ -z "$CLUSTER_NAME" ]; then
    echo "ERROR: EKS Cluster Name is required"
    exit 1
fi

# Prompt for Docker image URI
read -p "Enter Docker Image URI (e.g., 123456789.dkr.ecr.us-east-1.amazonaws.com/resortslite:latest): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
    echo "ERROR: Docker Image URI is required"
    exit 1
fi

echo ""
echo "=== Application Configuration ==="
echo "The following environment variables will be configured for the application."
echo "Press Enter to skip any optional configuration."
echo ""

# Database Configuration
read -p "Enter Database URL (default: jdbc:h2:mem:resortdb): " SPRING_DATASOURCE_URL
SPRING_DATASOURCE_URL=${SPRING_DATASOURCE_URL:-jdbc:h2:mem:resortdb;DB_CLOSE_DELAY=-1}

read -p "Enter Database Username (default: sa): " SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_USERNAME=${SPRING_DATASOURCE_USERNAME:-sa}

read -sp "Enter Database Password (press Enter for empty): " SPRING_DATASOURCE_PASSWORD
echo ""
SPRING_DATASOURCE_PASSWORD=${SPRING_DATASOURCE_PASSWORD:-}

# Redis Configuration
read -p "Enter Redis Host (e.g., redis.example.com): " REDIS_HOST
REDIS_HOST=${REDIS_HOST:-localhost}

read -p "Enter Redis Port (default: 6379): " REDIS_PORT
REDIS_PORT=${REDIS_PORT:-6379}

read -sp "Enter Redis Password (press Enter for empty): " REDIS_PASSWORD
echo ""
REDIS_PASSWORD=${REDIS_PASSWORD:-}

# External Service Endpoints
read -p "Enter Payment API URL (or press Enter to skip): " PAYMENT_API_URL
PAYMENT_API_URL=${PAYMENT_API_URL:-http://payment-service:9090/payments/charge}

read -p "Enter Payment Endpoint (or press Enter to skip): " APP_PAYMENT_ENDPOINT
APP_PAYMENT_ENDPOINT=${APP_PAYMENT_ENDPOINT:-http://payment-svc.internal:9090/charge}

read -p "Enter Inventory Endpoint (or press Enter to skip): " APP_INVENTORY_ENDPOINT
APP_INVENTORY_ENDPOINT=${APP_INVENTORY_ENDPOINT:-http://inventory-svc.internal:8081/rooms}

read -p "Enter Notification Endpoint (or press Enter to skip): " APP_NOTIFICATION_ENDPOINT
APP_NOTIFICATION_ENDPOINT=${APP_NOTIFICATION_ENDPOINT:-http://notify.internal:7070/send}

echo ""
echo "=========================================="
echo "Configuration Summary"
echo "=========================================="
echo "AWS Region: $AWS_REGION"
echo "EKS Cluster: $CLUSTER_NAME"
echo "Docker Image: $IMAGE_URI"
echo "Database URL: $SPRING_DATASOURCE_URL"
echo "Redis Host: $REDIS_HOST"
echo "=========================================="
echo ""

read -p "Proceed with deployment? (yes/no): " CONFIRM
if [ "$CONFIRM" != "yes" ]; then
    echo "Deployment cancelled."
    exit 0
fi

# Configure kubectl for EKS
echo ""
echo "Configuring kubectl for EKS cluster..."
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to configure kubectl for EKS cluster"
    exit 1
fi

# Verify cluster connectivity
echo "Verifying cluster connectivity..."
kubectl cluster-info || {
    echo "ERROR: Cannot connect to Kubernetes cluster"
    exit 1
}

echo ""
echo "✓ Connected to EKS cluster successfully"

# Create temporary directory for processed manifests
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT

# Copy manifests to temp directory
cp -r kubernetes/* "$TEMP_DIR/"

# Replace placeholders in deployment.yaml
echo ""
echo "Updating Kubernetes manifests..."
sed -i "s|{{IMAGE_URI}}|$IMAGE_URI|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{SPRING_DATASOURCE_URL}}|$SPRING_DATASOURCE_URL|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{SPRING_DATASOURCE_USERNAME}}|$SPRING_DATASOURCE_USERNAME|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{SPRING_DATASOURCE_PASSWORD}}|$SPRING_DATASOURCE_PASSWORD|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{REDIS_HOST}}|$REDIS_HOST|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{REDIS_PORT}}|$REDIS_PORT|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{REDIS_PASSWORD}}|$REDIS_PASSWORD|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{PAYMENT_API_URL}}|$PAYMENT_API_URL|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{APP_PAYMENT_ENDPOINT}}|$APP_PAYMENT_ENDPOINT|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{APP_INVENTORY_ENDPOINT}}|$APP_INVENTORY_ENDPOINT|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{APP_NOTIFICATION_ENDPOINT}}|$APP_NOTIFICATION_ENDPOINT|g" "$TEMP_DIR/deployment.yaml"

echo "✓ Manifests updated successfully"

# Apply Kubernetes manifests
echo ""
echo "=========================================="
echo "Deploying to Kubernetes..."
echo "=========================================="

echo ""
echo "Creating namespace..."
kubectl apply -f "$TEMP_DIR/namespace.yaml"

echo ""
echo "Deploying application..."
kubectl apply -f "$TEMP_DIR/deployment.yaml"

echo ""
echo "Creating service..."
kubectl apply -f "$TEMP_DIR/service.yaml"

echo ""
echo "Creating ingress..."
kubectl apply -f "$TEMP_DIR/ingress.yaml"

# Wait for deployment rollout
echo ""
echo "Waiting for deployment to complete..."
kubectl rollout status deployment/resortslite -n resortslite --timeout=5m

if [ $? -ne 0 ]; then
    echo "ERROR: Deployment rollout failed"
    echo ""
    echo "Checking pod status..."
    kubectl get pods -n resortslite
    echo ""
    echo "Checking pod logs..."
    kubectl logs -n resortslite -l app=resortslite --tail=50
    exit 1
fi

# Verify deployment
echo ""
echo "=========================================="
echo "Verifying Deployment..."
echo "=========================================="
kubectl get pods,svc,ingress -n resortslite

# Get ingress URL
echo ""
echo "=========================================="
echo "Deployment Complete!"
echo "=========================================="
echo ""
INGRESS_URL=$(kubectl get ingress resortslite-ingress -n resortslite -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "pending")

if [ "$INGRESS_URL" != "pending" ] && [ -n "$INGRESS_URL" ]; then
    echo "Application URL: http://$INGRESS_URL"
    echo "Health Check: http://$INGRESS_URL/actuator/health"
else
    echo "Ingress is being provisioned. Run the following command to get the URL:"
    echo "kubectl get ingress resortslite-ingress -n resortslite"
fi

echo ""
echo "Useful commands:"
echo "  View pods:        kubectl get pods -n resortslite"
echo "  View logs:        kubectl logs -n resortslite -l app=resortslite"
echo "  Describe pod:     kubectl describe pod -n resortslite -l app=resortslite"
echo "  Scale deployment: kubectl scale deployment resortslite -n resortslite --replicas=3"
echo "  Delete deployment: kubectl delete namespace resortslite"
echo ""
echo "=========================================="
