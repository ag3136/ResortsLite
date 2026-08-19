#!/bin/bash

# ============================================
# Deploy to AWS EKS Script
# For ResortsLite Spring Boot Application
# ============================================

set -e
set -o pipefail

echo "=========================================="
echo "AWS EKS Deployment Script"
echo "=========================================="
echo ""

# Prompt for AWS configuration
read -p "Enter AWS Region (e.g., us-east-1): " AWS_REGION
read -p "Enter EKS Cluster Name: " CLUSTER_NAME

echo ""
echo "AWS Region: $AWS_REGION"
echo "EKS Cluster: $CLUSTER_NAME"
echo ""

# Prompt for Docker image URI
read -p "Enter Docker Image URI (e.g., 123456789012.dkr.ecr.us-east-1.amazonaws.com/resortslite:latest): " IMAGE_URI

echo ""
echo "Docker Image: $IMAGE_URI"
echo ""

# Prompt for environment variables
echo "=========================================="
echo "Environment Configuration"
echo "=========================================="
echo "Enter values for environment variables (or press Enter to skip):"
echo ""

read -p "SPRING_DATASOURCE_URL (default: jdbc:h2:mem:resortdb): " SPRING_DATASOURCE_URL
SPRING_DATASOURCE_URL=${SPRING_DATASOURCE_URL:-jdbc:h2:mem:resortdb;DB_CLOSE_DELAY=-1}

read -p "SPRING_DATASOURCE_USERNAME (default: sa): " SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_USERNAME=${SPRING_DATASOURCE_USERNAME:-sa}

read -sp "SPRING_DATASOURCE_PASSWORD (default: empty): " SPRING_DATASOURCE_PASSWORD
echo ""
SPRING_DATASOURCE_PASSWORD=${SPRING_DATASOURCE_PASSWORD:-}

read -p "REDIS_HOST (e.g., redis.example.com): " REDIS_HOST
REDIS_HOST=${REDIS_HOST:-redis.example.com}

read -p "REDIS_PORT (default: 6379): " REDIS_PORT
REDIS_PORT=${REDIS_PORT:-6379}

read -sp "REDIS_PASSWORD (default: empty): " REDIS_PASSWORD
echo ""
REDIS_PASSWORD=${REDIS_PASSWORD:-}

read -p "PAYMENT_SERVICE_URL (default: http://payment-service:9090/payments/charge): " PAYMENT_SERVICE_URL
PAYMENT_SERVICE_URL=${PAYMENT_SERVICE_URL:-http://payment-service:9090/payments/charge}

read -p "APP_PAYMENT_ENDPOINT (default: http://payment-svc.internal:9090/charge): " APP_PAYMENT_ENDPOINT
APP_PAYMENT_ENDPOINT=${APP_PAYMENT_ENDPOINT:-http://payment-svc.internal:9090/charge}

read -p "APP_INVENTORY_ENDPOINT (default: http://inventory-svc.internal:8081/rooms): " APP_INVENTORY_ENDPOINT
APP_INVENTORY_ENDPOINT=${APP_INVENTORY_ENDPOINT:-http://inventory-svc.internal:8081/rooms}

read -p "APP_NOTIFICATION_ENDPOINT (default: http://notify.internal:7070/send): " APP_NOTIFICATION_ENDPOINT
APP_NOTIFICATION_ENDPOINT=${APP_NOTIFICATION_ENDPOINT:-http://notify.internal:7070/send}

echo ""
echo "=========================================="
echo "Configuring kubectl for EKS..."
echo "=========================================="

# Configure kubectl to use EKS cluster
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to configure kubectl for EKS cluster"
    exit 1
fi

echo "kubectl configured successfully"
echo ""

# Verify cluster connectivity
echo "Verifying cluster connectivity..."
kubectl cluster-info || {
    echo "ERROR: Cannot connect to Kubernetes cluster"
    exit 1
}

echo ""
echo "=========================================="
echo "Updating Kubernetes Manifests..."
echo "=========================================="

# Create temporary directory for updated manifests
TEMP_DIR=$(mktemp -d)
cp -r kubernetes/* "$TEMP_DIR/"

# Update deployment.yaml with image URI and environment variables
sed -i "s|{{IMAGE_URI}}|$IMAGE_URI|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{SPRING_DATASOURCE_URL}}|$SPRING_DATASOURCE_URL|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{SPRING_DATASOURCE_USERNAME}}|$SPRING_DATASOURCE_USERNAME|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{SPRING_DATASOURCE_PASSWORD}}|$SPRING_DATASOURCE_PASSWORD|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{REDIS_HOST}}|$REDIS_HOST|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{REDIS_PORT}}|$REDIS_PORT|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{REDIS_PASSWORD}}|$REDIS_PASSWORD|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{PAYMENT_SERVICE_URL}}|$PAYMENT_SERVICE_URL|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{APP_PAYMENT_ENDPOINT}}|$APP_PAYMENT_ENDPOINT|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{APP_INVENTORY_ENDPOINT}}|$APP_INVENTORY_ENDPOINT|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{APP_NOTIFICATION_ENDPOINT}}|$APP_NOTIFICATION_ENDPOINT|g" "$TEMP_DIR/deployment.yaml"

echo "Manifests updated successfully"
echo ""

# Apply Kubernetes manifests
echo "=========================================="
echo "Deploying to Kubernetes..."
echo "=========================================="

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

echo ""
echo "=========================================="
echo "Waiting for Deployment Rollout..."
echo "=========================================="

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

echo ""
echo "=========================================="
echo "Deployment Successful!"
echo "=========================================="

# Display deployment information
echo ""
echo "Deployment Information:"
kubectl get pods,svc,ingress -n resortslite

echo ""
echo "=========================================="
echo "Application Access Information"
echo "=========================================="

# Get ingress URL
INGRESS_URL=$(kubectl get ingress resortslite-ingress -n resortslite -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "Pending...")

echo "Ingress URL: $INGRESS_URL"
echo ""
echo "Note: It may take a few minutes for the Load Balancer to become available."
echo "You can check the status with: kubectl get ingress -n resortslite"
echo ""

# Cleanup temporary directory
rm -rf "$TEMP_DIR"

echo "=========================================="
echo "Deployment Complete!"
echo "=========================================="
echo ""
echo "Useful Commands:"
echo "  View pods:        kubectl get pods -n resortslite"
echo "  View logs:        kubectl logs -n resortslite -l app=resortslite"
echo "  View services:    kubectl get svc -n resortslite"
echo "  View ingress:     kubectl get ingress -n resortslite"
echo "  Scale deployment: kubectl scale deployment resortslite -n resortslite --replicas=3"
echo "  Rollback:         kubectl rollout undo deployment/resortslite -n resortslite"
echo "=========================================="
