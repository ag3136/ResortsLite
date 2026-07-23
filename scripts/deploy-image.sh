#!/bin/bash
set -e
set -o pipefail

APP_NAME="resortslite"
NAMESPACE="resortslite"
K8S_DIR="kubernetes"

echo "============================================"
echo "  ResortsLite — Deploy to AWS EKS"
echo "============================================"
echo ""

# ── AWS / EKS configuration ──────────────────────────────────────────────────
read -rp "Enter AWS region (e.g. us-east-1): " AWS_REGION
if [ -z "$AWS_REGION" ]; then
  echo "ERROR: AWS region is required."
  exit 1
fi

read -rp "Enter EKS cluster name: " CLUSTER_NAME
if [ -z "$CLUSTER_NAME" ]; then
  echo "ERROR: EKS cluster name is required."
  exit 1
fi

read -rp "Enter full Docker image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/resortslite:latest): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
  echo "ERROR: Image URI is required."
  exit 1
fi

# ── Application environment variables ────────────────────────────────────────
echo ""
echo "--- Application Configuration (press Enter to use defaults) ---"

read -rp "Enter REDIS_HOST (default: localhost): " REDIS_HOST
REDIS_HOST="${REDIS_HOST:-localhost}"

read -rp "Enter REDIS_PORT (default: 6379): " REDIS_PORT
REDIS_PORT="${REDIS_PORT:-6379}"

read -rp "Enter PAYMENT_API_URL (default: http://payment-service.default.svc.cluster.local:9090/payments/charge): " PAYMENT_API_URL
PAYMENT_API_URL="${PAYMENT_API_URL:-http://payment-service.default.svc.cluster.local:9090/payments/charge}"

read -rp "Enter REPORT_BASE_PATH (default: /var/reports): " REPORT_BASE_PATH
REPORT_BASE_PATH="${REPORT_BASE_PATH:-/var/reports}"

read -rp "Enter BACKUP_PATH (default: /var/backups/resorts): " BACKUP_PATH
BACKUP_PATH="${BACKUP_PATH:-/var/backups/resorts}"

read -rp "Enter CACHE_PROVIDER (default: redis): " CACHE_PROVIDER
CACHE_PROVIDER="${CACHE_PROVIDER:-redis}"

# ── Configure kubectl ─────────────────────────────────────────────────────────
echo ""
echo "Configuring kubectl for EKS cluster: $CLUSTER_NAME in $AWS_REGION ..."
aws eks update-kubeconfig --region "$AWS_REGION" --name "$CLUSTER_NAME"

echo "Verifying cluster connectivity..."
kubectl cluster-info || { echo "ERROR: Cannot connect to EKS cluster."; exit 1; }

# ── Substitute placeholders in manifests ─────────────────────────────────────
echo ""
echo "Updating Kubernetes manifests with deployment values..."

sed -i "s|{{IMAGE_URI}}|${IMAGE_URI}|g"               "${K8S_DIR}/deployment.yaml"
sed -i "s|{{REDIS_HOST}}|${REDIS_HOST}|g"             "${K8S_DIR}/deployment.yaml"
sed -i "s|{{REDIS_PORT}}|${REDIS_PORT}|g"             "${K8S_DIR}/deployment.yaml"
sed -i "s|{{PAYMENT_API_URL}}|${PAYMENT_API_URL}|g"   "${K8S_DIR}/deployment.yaml"
sed -i "s|{{REPORT_BASE_PATH}}|${REPORT_BASE_PATH}|g" "${K8S_DIR}/deployment.yaml"
sed -i "s|{{BACKUP_PATH}}|${BACKUP_PATH}|g"           "${K8S_DIR}/deployment.yaml"
sed -i "s|{{CACHE_PROVIDER}}|${CACHE_PROVIDER}|g"     "${K8S_DIR}/deployment.yaml"

# ── Apply manifests ───────────────────────────────────────────────────────────
echo ""
echo "Applying Kubernetes manifests..."

echo "  [1/4] Applying namespace..."
kubectl apply -f "${K8S_DIR}/namespace.yaml"

echo "  [2/4] Applying deployment..."
kubectl apply -f "${K8S_DIR}/deployment.yaml"

echo "  [3/4] Applying service..."
kubectl apply -f "${K8S_DIR}/service.yaml"

echo "  [4/4] Applying ingress..."
kubectl apply -f "${K8S_DIR}/ingress.yaml"

# ── Wait for rollout ──────────────────────────────────────────────────────────
echo ""
echo "Waiting for deployment rollout to complete..."
kubectl rollout status deployment/"${APP_NAME}" -n "${NAMESPACE}" --timeout=300s

# ── Verify resources ──────────────────────────────────────────────────────────
echo ""
echo "Verifying deployed resources..."
kubectl get pods,svc,ingress -n "${NAMESPACE}"

# ── Display access URL ────────────────────────────────────────────────────────
echo ""
INGRESS_HOST=$(kubectl get ingress "${APP_NAME}-ingress" -n "${NAMESPACE}" \
  -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "pending")

echo "============================================"
echo "  Deployment complete!"
echo "  Application URL: http://${INGRESS_HOST}"
echo "  Health check:    http://${INGRESS_HOST}/actuator/health"
echo "============================================"
echo ""
echo "Rollback command (if needed):"
echo "  kubectl rollout undo deployment/${APP_NAME} -n ${NAMESPACE}"
