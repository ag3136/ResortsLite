#!/bin/bash

# Deploy ResortsLite to AWS EKS
# This script configures kubectl and deploys the application to EKS

set -e
set -o pipefail

echo "=========================================="
echo "ResortsLite - AWS EKS Deployment Script"
echo "=========================================="
echo ""

# Prompt for AWS region
read -p "Enter AWS Region (e.g., us-east-1): " AWS_REGION
if [ -z "$AWS_REGION" ]; then
    echo "Error: AWS Region is required"
    exit 1
fi

# Prompt for EKS cluster name
read -p "Enter EKS Cluster Name: " CLUSTER_NAME
if [ -z "$CLUSTER_NAME" ]; then
    echo "Error: EKS Cluster Name is required"
    exit 1
fi

# Prompt for Docker image URI
read -p "Enter Docker Image URI (e.g., 123456789012.dkr.ecr.us-east-1.amazonaws.com/resortslite:latest): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
    echo "Error: Docker Image URI is required"
    exit 1
fi

echo ""
echo "=== Environment Configuration ==="
echo "The following environment variables are used by the application."
echo "Press Enter to skip optional variables or provide values."
echo ""

# Prompt for database configuration
read -p "Enter SPRING_DATASOURCE_URL (default: jdbc:h2:mem:resortdb): " SPRING_DATASOURCE_URL
SPRING_DATASOURCE_URL=${SPRING_DATASOURCE_URL:-jdbc:h2:mem:resortdb;DB_CLOSE_DELAY=-1}

read -p "Enter SPRING_DATASOURCE_USERNAME (default: sa): " SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_USERNAME=${SPRING_DATASOURCE_USERNAME:-sa}

read -sp "Enter SPRING_DATASOURCE_PASSWORD (default: empty): " SPRING_DATASOURCE_PASSWORD
echo ""
SPRING_DATASOURCE_PASSWORD=${SPRING_DATASOURCE_PASSWORD:-}

# Prompt for external service endpoints
read -p "Enter APP_PAYMENT_ENDPOINT (default: http://payment-svc:9090/charge): " APP_PAYMENT_ENDPOINT
APP_PAYMENT_ENDPOINT=${APP_PAYMENT_ENDPOINT:-http://payment-svc:9090/charge}

read -p "Enter APP_INVENTORY_ENDPOINT (default: http://inventory-svc:8081/rooms): " APP_INVENTORY_ENDPOINT
APP_INVENTORY_ENDPOINT=${APP_INVENTORY_ENDPOINT:-http://inventory-svc:8081/rooms}

read -p "Enter APP_NOTIFICATION_ENDPOINT (default: http://notify-svc:7070/send): " APP_NOTIFICATION_ENDPOINT
APP_NOTIFICATION_ENDPOINT=${APP_NOTIFICATION_ENDPOINT:-http://notify-svc:7070/send}

echo ""
echo "=========================================="
echo "Configuring kubectl for EKS"
echo "=========================================="
echo ""

# Configure kubectl to use EKS cluster
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"

if [ $? -ne 0 ]; then
    echo "Error: Failed to configure kubectl for EKS cluster"
    exit 1
fi

echo "kubectl configured successfully"

# Verify cluster connectivity
echo ""
echo "Verifying cluster connectivity..."
kubectl cluster-info

if [ $? -ne 0 ]; then
    echo "Error: Cannot connect to EKS cluster"
    exit 1
fi

echo ""
echo "=========================================="
echo "Updating Kubernetes Manifests"
echo "=========================================="
echo ""

# Create temporary directory for processed manifests
TEMP_DIR=$(mktemp -d)
echo "Using temporary directory: $TEMP_DIR"

# Copy manifests to temp directory
cp -r kubernetes/* "$TEMP_DIR/"

# Replace placeholders in deployment.yaml
echo "Updating deployment.yaml with image URI and environment variables..."
sed -i "s|{{IMAGE_URI}}|$IMAGE_URI|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{SPRING_DATASOURCE_URL}}|$SPRING_DATASOURCE_URL|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{SPRING_DATASOURCE_USERNAME}}|$SPRING_DATASOURCE_USERNAME|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{SPRING_DATASOURCE_PASSWORD}}|$SPRING_DATASOURCE_PASSWORD|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{APP_PAYMENT_ENDPOINT}}|$APP_PAYMENT_ENDPOINT|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{APP_INVENTORY_ENDPOINT}}|$APP_INVENTORY_ENDPOINT|g" "$TEMP_DIR/deployment.yaml"
sed -i "s|{{APP_NOTIFICATION_ENDPOINT}}|$APP_NOTIFICATION_ENDPOINT|g" "$TEMP_DIR/deployment.yaml"

echo "Manifests updated successfully"

echo ""
echo "=========================================="
echo "Deploying to AWS EKS"
echo "=========================================="
echo ""

# Apply namespace
echo "Creating namespace..."
kubectl apply -f "$TEMP_DIR/namespace.yaml"

if [ $? -ne 0 ]; then
    echo "Error: Failed to create namespace"
    rm -rf "$TEMP_DIR"
    exit 1
fi

# Apply deployment
echo ""
echo "Creating deployment..."
kubectl apply -f "$TEMP_DIR/deployment.yaml"

if [ $? -ne 0 ]; then
    echo "Error: Failed to create deployment"
    rm -rf "$TEMP_DIR"
    exit 1
fi

# Apply service
echo ""
echo "Creating service..."
kubectl apply -f "$TEMP_DIR/service.yaml"

if [ $? -ne 0 ]; then
    echo "Error: Failed to create service"
    rm -rf "$TEMP_DIR"
    exit 1
fi

# Apply ingress
echo ""
echo "Creating ingress..."
kubectl apply -f "$TEMP_DIR/ingress.yaml"

if [ $? -ne 0 ]; then
    echo "Error: Failed to create ingress"
    rm -rf "$TEMP_DIR"
    exit 1
fi

# Clean up temporary directory
rm -rf "$TEMP_DIR"

echo ""
echo "=========================================="
echo "Waiting for Deployment Rollout"
echo "=========================================="
echo ""

# Wait for deployment to complete
kubectl rollout status deployment/resortslite -n resortslite --timeout=5m

if [ $? -ne 0 ]; then
    echo "Error: Deployment rollout failed or timed out"
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
echo "Deployment Verification"
echo "=========================================="
echo ""

# Display deployed resources
echo "Pods:"
kubectl get pods -n resortslite -o wide

echo ""
echo "Services:"
kubectl get svc -n resortslite

echo ""
echo "Ingress:"
kubectl get ingress -n resortslite

echo ""
echo "=========================================="
echo "Deployment Completed Successfully"
echo "=========================================="
echo ""

# Get ingress URL
INGRESS_URL=$(kubectl get ingress resortslite-ingress -n resortslite -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null)

if [ -n "$INGRESS_URL" ]; then
    echo "Application URL: http://$INGRESS_URL"
    echo "Health Check: http://$INGRESS_URL/actuator/health"
else
    echo "Ingress URL not yet available. Run the following command to check:"
    echo "kubectl get ingress resortslite-ingress -n resortslite"
fi

echo ""
echo "Useful commands:"
echo "  View pods:        kubectl get pods -n resortslite"
echo "  View logs:        kubectl logs -n resortslite -l app=resortslite"
echo "  Describe pod:     kubectl describe pod <pod-name> -n resortslite"
echo "  Scale deployment: kubectl scale deployment resortslite -n resortslite --replicas=3"
echo "  Delete deployment: kubectl delete namespace resortslite"
echo ""
