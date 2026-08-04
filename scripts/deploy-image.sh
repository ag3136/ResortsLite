#!/bin/bash

# Deploy ResortsLite to GCP GKE
# This script deploys the containerized application to Google Kubernetes Engine

set -e
set -o pipefail

echo "=========================================="
echo "ResortsLite - GKE Deployment Script"
echo "=========================================="
echo ""

# Prompt for GCP configuration
read -p "Enter GCP Project ID: " GCP_PROJECT
if [ -z "$GCP_PROJECT" ]; then
    echo "ERROR: GCP Project ID is required!"
    exit 1
fi

read -p "Enter GCP Zone (e.g., us-central1-a): " GCP_ZONE
if [ -z "$GCP_ZONE" ]; then
    echo "ERROR: GCP Zone is required!"
    exit 1
fi

read -p "Enter GKE Cluster Name: " CLUSTER_NAME
if [ -z "$CLUSTER_NAME" ]; then
    echo "ERROR: GKE Cluster Name is required!"
    exit 1
fi

read -p "Enter Docker Image URI (with tag): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
    echo "ERROR: Docker Image URI is required!"
    exit 1
fi

echo ""
echo "=== Application Configuration ==="
read -p "Enter Redis Host (or press Enter to skip): " REDIS_HOST
REDIS_HOST=${REDIS_HOST:-localhost}

read -p "Enter Redis Port (default: 6379): " REDIS_PORT
REDIS_PORT=${REDIS_PORT:-6379}

read -p "Enter Redis Password (or press Enter for none): " REDIS_PASSWORD

read -p "Enter Inventory Service URL (or press Enter for default): " INVENTORY_SERVICE_URL
INVENTORY_SERVICE_URL=${INVENTORY_SERVICE_URL:-http://localhost:8080}

echo ""
echo "=========================================="
echo "Configuring kubectl for GKE..."
echo "=========================================="
gcloud container clusters get-credentials "$CLUSTER_NAME" --zone "$GCP_ZONE" --project "$GCP_PROJECT"

if [ $? -ne 0 ]; then
    echo "ERROR: Failed to configure kubectl!"
    exit 1
fi

echo ""
echo "Verifying cluster connectivity..."
kubectl cluster-info || exit 1

echo ""
echo "=========================================="
echo "Updating Kubernetes manifests..."
echo "=========================================="

# Update deployment.yaml with actual values
sed -i "s|{{IMAGE_URI}}|$IMAGE_URI|g" kubernetes/deployment.yaml
sed -i "s|{{REDIS_HOST}}|$REDIS_HOST|g" kubernetes/deployment.yaml
sed -i "s|{{REDIS_PORT}}|$REDIS_PORT|g" kubernetes/deployment.yaml
sed -i "s|{{REDIS_PASSWORD}}|$REDIS_PASSWORD|g" kubernetes/deployment.yaml
sed -i "s|{{INVENTORY_SERVICE_URL}}|$INVENTORY_SERVICE_URL|g" kubernetes/deployment.yaml

echo "Manifests updated successfully."

echo ""
echo "=========================================="
echo "Applying Kubernetes manifests..."
echo "=========================================="

echo "Creating namespace..."
kubectl apply -f kubernetes/namespace.yaml

echo ""
echo "Deploying application..."
kubectl apply -f kubernetes/deployment.yaml

echo ""
echo "Creating service..."
kubectl apply -f kubernetes/service.yaml

echo ""
echo "Creating ingress..."
kubectl apply -f kubernetes/ingress.yaml

echo ""
echo "=========================================="
echo "Waiting for deployment rollout..."
echo "=========================================="
kubectl rollout status deployment/resortslite -n resortslite --timeout=5m

if [ $? -ne 0 ]; then
    echo "ERROR: Deployment rollout failed!"
    echo "Check pod status with: kubectl get pods -n resortslite"
    echo "Check logs with: kubectl logs -n resortslite -l app=resortslite"
    exit 1
fi

echo ""
echo "=========================================="
echo "Deployment Status"
echo "=========================================="
kubectl get pods,svc,ingress -n resortslite

echo ""
echo "=========================================="
echo "SUCCESS!"
echo "=========================================="
echo "ResortsLite has been deployed to GKE successfully!"
echo ""
echo "Useful commands:"
echo "  View pods:        kubectl get pods -n resortslite"
echo "  View logs:        kubectl logs -n resortslite -l app=resortslite"
echo "  View service:     kubectl get svc -n resortslite"
echo "  View ingress:     kubectl get ingress -n resortslite"
echo "  Scale deployment: kubectl scale deployment/resortslite -n resortslite --replicas=3"
echo ""
echo "To access the application:"
echo "  1. Get the ingress IP: kubectl get ingress resortslite-ingress -n resortslite"
echo "  2. Access via: http://<INGRESS_IP>/"
echo "=========================================="
