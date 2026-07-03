#!/bin/bash
set -e
set -o pipefail

# ============================================================
# deploy-image.sh — Deploy BRDresort to Azure AKS
# ============================================================

APP_NAME="brdresort"
NAMESPACE="brdresort"
K8S_DIR="$(cd "$(dirname "$0")/.." && pwd)/kubernetes"

echo "============================================"
echo "  BRDresort — Deploy to Azure AKS"
echo "============================================"
echo ""

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
read -rp "Enter full Docker image URI (e.g. myregistry.azurecr.io/brdresort:latest): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
  echo "ERROR: Image URI cannot be empty." >&2
  exit 1
fi

echo ""
echo "---- Application Environment Variables ----"
echo "Press Enter to keep the placeholder (you can update later in the YAML)."
echo ""

read -rp "Enter REDIS_HOST [redis-service]: " REDIS_HOST_VAL
REDIS_HOST_VAL="${REDIS_HOST_VAL:-redis-service}"

read -rp "Enter REDIS_PORT [6379]: " REDIS_PORT_VAL
REDIS_PORT_VAL="${REDIS_PORT_VAL:-6379}"

read -rp "Enter REDIS_PASSWORD (leave blank if none): " REDIS_PASSWORD_VAL

read -rp "Enter AZURE_BLOB_REPORTS_URL: " AZURE_BLOB_REPORTS_URL_VAL
AZURE_BLOB_REPORTS_URL_VAL="${AZURE_BLOB_REPORTS_URL_VAL:-https://storageaccount.blob.core.windows.net/reports}"

read -rp "Enter AZURE_BLOB_BACKUP_URL: " AZURE_BLOB_BACKUP_URL_VAL
AZURE_BLOB_BACKUP_URL_VAL="${AZURE_BLOB_BACKUP_URL_VAL:-https://storageaccount.blob.core.windows.net/backups}"

read -rp "Enter PAYMENT_API_URL: " PAYMENT_API_URL_VAL
PAYMENT_API_URL_VAL="${PAYMENT_API_URL_VAL:-http://payment-service/payments/charge}"

# ---- Substitute placeholders in deployment.yaml ----
echo ""
echo "Substituting placeholders in Kubernetes manifests ..."

DEPLOY_YAML="${K8S_DIR}/deployment.yaml"
DEPLOY_YAML_TMP="${K8S_DIR}/deployment.yaml.tmp"

cp "$DEPLOY_YAML" "$DEPLOY_YAML_TMP"

sed -i "s|{{IMAGE_URI}}|${IMAGE_URI}|g"                                   "$DEPLOY_YAML_TMP"
sed -i "s|{{REDIS_HOST}}|${REDIS_HOST_VAL}|g"                             "$DEPLOY_YAML_TMP"
sed -i "s|{{REDIS_PORT}}|${REDIS_PORT_VAL}|g"                             "$DEPLOY_YAML_TMP"
sed -i "s|{{REDIS_PASSWORD}}|${REDIS_PASSWORD_VAL}|g"                     "$DEPLOY_YAML_TMP"
sed -i "s|{{AZURE_BLOB_REPORTS_URL}}|${AZURE_BLOB_REPORTS_URL_VAL}|g"     "$DEPLOY_YAML_TMP"
sed -i "s|{{AZURE_BLOB_BACKUP_URL}}|${AZURE_BLOB_BACKUP_URL_VAL}|g"       "$DEPLOY_YAML_TMP"
sed -i "s|{{PAYMENT_API_URL}}|${PAYMENT_API_URL_VAL}|g"                   "$DEPLOY_YAML_TMP"

# ---- Configure kubectl for AKS ----
echo ""
echo "Configuring kubectl for AKS cluster: $CLUSTER_NAME ..."
az aks get-credentials --resource-group "$RESOURCE_GROUP" --name "$CLUSTER_NAME" --overwrite-existing

echo ""
echo "Verifying cluster connectivity ..."
kubectl cluster-info || { echo "ERROR: Cannot connect to AKS cluster." >&2; exit 1; }

# ---- Apply manifests ----
echo ""
echo "Applying Kubernetes manifests ..."

echo "  [1/4] Applying namespace ..."
kubectl apply -f "${K8S_DIR}/namespace.yaml"

echo "  [2/4] Applying deployment ..."
kubectl apply -f "$DEPLOY_YAML_TMP"

echo "  [3/4] Applying service ..."
kubectl apply -f "${K8S_DIR}/service.yaml"

echo "  [4/4] Applying ingress ..."
kubectl apply -f "${K8S_DIR}/ingress.yaml"

# Clean up temp file
rm -f "$DEPLOY_YAML_TMP"

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
INGRESS_IP=$(kubectl get ingress ${APP_NAME}-ingress -n ${NAMESPACE} -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "pending")
echo "============================================"
echo "  Deployment Complete!"
echo "  Namespace : ${NAMESPACE}"
echo "  Image     : ${IMAGE_URI}"
if [ "$INGRESS_IP" != "pending" ] && [ -n "$INGRESS_IP" ]; then
  echo "  App URL   : http://${INGRESS_IP}"
else
  echo "  App URL   : http://brdresort.example.com (update DNS to point to ingress IP)"
  echo "  Ingress IP: Run 'kubectl get ingress -n ${NAMESPACE}' once DNS propagates"
fi
echo "============================================"
echo ""
echo "Rollback command (if needed):"
echo "  kubectl rollout undo deployment/${APP_NAME} -n ${NAMESPACE}"
