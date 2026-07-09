#!/bin/bash
set -e
set -o pipefail

# ============================================================
# deploy-image.sh — Deploy ResortsLite to Azure AKS
# Usage: ./scripts/deploy-image.sh
# Run from repository root directory
# Prerequisites: azure-cli, kubectl
# ============================================================

APP_NAME="resortslite"
NAMESPACE="resortslite"
MANIFESTS_DIR="kubernetes"

echo "============================================"
echo "  ResortsLite — Deploy to Azure AKS"
echo "============================================"

# ---- Azure / AKS credentials ----
read -rp "Enter Azure Resource Group name: " RESOURCE_GROUP
if [ -z "$RESOURCE_GROUP" ]; then
  echo "ERROR: Resource group cannot be empty." >&2
  exit 1
fi

read -rp "Enter AKS Cluster name: " CLUSTER_NAME
if [ -z "$CLUSTER_NAME" ]; then
  echo "ERROR: AKS cluster name cannot be empty." >&2
  exit 1
fi

# ---- Docker image URI ----
read -rp "Enter full Docker image URI (e.g. myregistry.azurecr.io/resortslite:latest): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
  echo "ERROR: Image URI cannot be empty." >&2
  exit 1
fi

# ---- Application environment variables ----
echo ""
echo "--- Application Environment Variables ---"
echo "(Press Enter to skip any variable and keep the placeholder)"

read -rp "Enter REDIS_HOST (Azure Cache for Redis hostname): " REDIS_HOST
REDIS_HOST="${REDIS_HOST:-localhost}"

read -rp "Enter REDIS_PORT [6379]: " REDIS_PORT
REDIS_PORT="${REDIS_PORT:-6379}"

read -rsp "Enter REDIS_PASSWORD (leave blank if none): " REDIS_PASSWORD
echo ""
REDIS_PASSWORD="${REDIS_PASSWORD:-}"

read -rp "Enter REDIS_SSL [false]: " REDIS_SSL
REDIS_SSL="${REDIS_SSL:-false}"

read -rp "Enter APP_PAYMENT_ENDPOINT [http://payment-svc:9090/charge]: " APP_PAYMENT_ENDPOINT
APP_PAYMENT_ENDPOINT="${APP_PAYMENT_ENDPOINT:-http://payment-svc:9090/charge}"

read -rp "Enter APP_INVENTORY_ENDPOINT [http://inventory-svc:8081/rooms]: " APP_INVENTORY_ENDPOINT
APP_INVENTORY_ENDPOINT="${APP_INVENTORY_ENDPOINT:-http://inventory-svc:8081/rooms}"

read -rp "Enter APP_NOTIFICATION_ENDPOINT [http://notify-svc:7070/send]: " APP_NOTIFICATION_ENDPOINT
APP_NOTIFICATION_ENDPOINT="${APP_NOTIFICATION_ENDPOINT:-http://notify-svc:7070/send}"

# ---- Configure kubectl for AKS ----
echo ""
echo "Configuring kubectl for AKS cluster: ${CLUSTER_NAME} ..."
az aks get-credentials --resource-group "$RESOURCE_GROUP" --name "$CLUSTER_NAME" --overwrite-existing

echo "Verifying cluster connectivity ..."
kubectl cluster-info || { echo "ERROR: Cannot connect to AKS cluster." >&2; exit 1; }

# ---- Patch manifests with actual values ----
echo ""
echo "Updating Kubernetes manifests with deployment values ..."

# Work on copies to avoid modifying originals
cp -r "${MANIFESTS_DIR}" /tmp/resortslite-k8s-deploy

sed -i "s|{{IMAGE_URI}}|${IMAGE_URI}|g"                           /tmp/resortslite-k8s-deploy/deployment.yaml
sed -i "s|{{REDIS_HOST}}|${REDIS_HOST}|g"                         /tmp/resortslite-k8s-deploy/deployment.yaml
sed -i "s|{{REDIS_PORT}}|${REDIS_PORT}|g"                         /tmp/resortslite-k8s-deploy/deployment.yaml
sed -i "s|{{REDIS_PASSWORD}}|${REDIS_PASSWORD}|g"                 /tmp/resortslite-k8s-deploy/deployment.yaml
sed -i "s|{{REDIS_SSL}}|${REDIS_SSL}|g"                           /tmp/resortslite-k8s-deploy/deployment.yaml
sed -i "s|{{APP_PAYMENT_ENDPOINT}}|${APP_PAYMENT_ENDPOINT}|g"     /tmp/resortslite-k8s-deploy/deployment.yaml
sed -i "s|{{APP_INVENTORY_ENDPOINT}}|${APP_INVENTORY_ENDPOINT}|g" /tmp/resortslite-k8s-deploy/deployment.yaml
sed -i "s|{{APP_NOTIFICATION_ENDPOINT}}|${APP_NOTIFICATION_ENDPOINT}|g" /tmp/resortslite-k8s-deploy/deployment.yaml

# ---- Apply manifests in order ----
echo ""
echo "Applying Kubernetes manifests ..."

echo "  [1/4] Applying namespace ..."
kubectl apply -f /tmp/resortslite-k8s-deploy/namespace.yaml

echo "  [2/4] Applying deployment ..."
kubectl apply -f /tmp/resortslite-k8s-deploy/deployment.yaml

echo "  [3/4] Applying service ..."
kubectl apply -f /tmp/resortslite-k8s-deploy/service.yaml

echo "  [4/4] Applying ingress ..."
kubectl apply -f /tmp/resortslite-k8s-deploy/ingress.yaml

# ---- Wait for rollout ----
echo ""
echo "Waiting for deployment rollout ..."
kubectl rollout status deployment/${APP_NAME} -n ${NAMESPACE} --timeout=300s

# ---- Verify resources ----
echo ""
echo "Verifying deployed resources ..."
kubectl get pods,svc,ingress -n ${NAMESPACE}

# ---- Display access URL ----
echo ""
INGRESS_IP=$(kubectl get ingress resortslite-ingress -n ${NAMESPACE} -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "pending")
echo "============================================"
echo "  Deployment Complete!"
echo "  Namespace : ${NAMESPACE}"
echo "  Image     : ${IMAGE_URI}"
if [ "$INGRESS_IP" != "pending" ] && [ -n "$INGRESS_IP" ]; then
  echo "  App URL   : http://${INGRESS_IP}"
else
  echo "  App URL   : http://resortslite.example.com (update DNS to ingress IP)"
  echo "  Ingress IP: Run 'kubectl get ingress -n ${NAMESPACE}' once provisioned"
fi
echo "============================================"

echo ""
echo "Rollback command (if needed):"
echo "  kubectl rollout undo deployment/${APP_NAME} -n ${NAMESPACE}"

# ---- Cleanup temp files ----
rm -rf /tmp/resortslite-k8s-deploy
