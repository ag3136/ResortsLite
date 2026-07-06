#!/bin/bash
# =============================================================================
# deploy-image.sh — Deploy ResortsLite to AWS ECS Fargate
# Usage: ./scripts/deploy-image.sh
# Run from the repository root directory
# =============================================================================
set -e
set -o pipefail

PROJECT_NAME="resortslite"
TASK_DEF_FILE="ecs/task-definition.json"
SERVICE_DEF_FILE="ecs/service-definition.json"
LOG_GROUP="/ecs/${PROJECT_NAME}"

echo "=============================================="
echo "  ResortsLite — ECS Fargate Deployment"
echo "=============================================="
echo ""

# ---------------------------------------------------------------------------
# Collect configuration
# ---------------------------------------------------------------------------
read -rp "Enter AWS region [us-east-1]: " AWS_REGION
AWS_REGION="${AWS_REGION:-us-east-1}"

read -rp "Enter ECS cluster name [resortslite-cluster]: " CLUSTER_NAME
CLUSTER_NAME="${CLUSTER_NAME:-resortslite-cluster}"

read -rp "Enter ECR image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/resortslite:latest): " IMAGE_URI
if [ -z "$IMAGE_URI" ]; then
  echo "ERROR: Image URI is required."
  exit 1
fi

read -rp "Enter Subnet 1 ID (e.g. subnet-xxxxxxxx): " SUBNET_1
read -rp "Enter Subnet 2 ID (e.g. subnet-yyyyyyyy): " SUBNET_2
read -rp "Enter Security Group ID (e.g. sg-xxxxxxxx): " SECURITY_GROUP
read -rp "Enter Redis host (ElastiCache endpoint) [localhost]: " REDIS_HOST
REDIS_HOST="${REDIS_HOST:-localhost}"

# ---------------------------------------------------------------------------
# Auto-detect AWS Account ID
# ---------------------------------------------------------------------------
echo ""
echo "Detecting AWS Account ID..."
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
echo "Account ID: $ACCOUNT_ID"

# ---------------------------------------------------------------------------
# Ensure CloudWatch log group exists
# ---------------------------------------------------------------------------
echo ""
echo "Ensuring CloudWatch log group exists: $LOG_GROUP"
aws logs describe-log-groups --log-group-name-prefix "$LOG_GROUP" --region "$AWS_REGION" \
  | grep -q "$LOG_GROUP" \
  || aws logs create-log-group --log-group-name "$LOG_GROUP" --region "$AWS_REGION"
echo "Log group ready: $LOG_GROUP"

# ---------------------------------------------------------------------------
# Ensure ECS cluster exists
# ---------------------------------------------------------------------------
echo ""
echo "Checking ECS cluster: $CLUSTER_NAME"
CLUSTER_STATUS=$(aws ecs describe-clusters --clusters "$CLUSTER_NAME" --region "$AWS_REGION" \
  --query "clusters[0].status" --output text 2>/dev/null || echo "MISSING")
if [ "$CLUSTER_STATUS" != "ACTIVE" ]; then
  echo "Creating ECS cluster: $CLUSTER_NAME"
  aws ecs create-cluster --cluster-name "$CLUSTER_NAME" --region "$AWS_REGION"
fi
echo "Cluster ready: $CLUSTER_NAME"

# ---------------------------------------------------------------------------
# Load balancer prompt
# ---------------------------------------------------------------------------
echo ""
read -rp "Do you need an Application Load Balancer for this service? (y/n) [n]: " NEED_LB
NEED_LB="${NEED_LB:-n}"

TARGET_GROUP_ARN=""
ALB_DNS=""

if [[ "$NEED_LB" =~ ^[Yy]$ ]]; then
  read -rp "Enter VPC ID for the ALB (e.g. vpc-xxxxxxxx): " VPC_ID

  echo ""
  echo "Creating Application Load Balancer..."
  ALB_ARN=$(aws elbv2 create-load-balancer \
    --name "${PROJECT_NAME}-alb" \
    --subnets "$SUBNET_1" "$SUBNET_2" \
    --security-groups "$SECURITY_GROUP" \
    --scheme internet-facing \
    --type application \
    --region "$AWS_REGION" \
    --query "LoadBalancers[0].LoadBalancerArn" --output text)
  echo "ALB ARN: $ALB_ARN"

  ALB_DNS=$(aws elbv2 describe-load-balancers \
    --load-balancer-arns "$ALB_ARN" \
    --region "$AWS_REGION" \
    --query "LoadBalancers[0].DNSName" --output text)

  echo "Creating Target Group (target-type: ip for Fargate awsvpc)..."
  TARGET_GROUP_ARN=$(aws elbv2 create-target-group \
    --name "${PROJECT_NAME}-tg" \
    --protocol HTTP \
    --port 8080 \
    --vpc-id "$VPC_ID" \
    --target-type ip \
    --health-check-path "/actuator/health" \
    --health-check-interval-seconds 30 \
    --healthy-threshold-count 2 \
    --unhealthy-threshold-count 3 \
    --region "$AWS_REGION" \
    --query "TargetGroups[0].TargetGroupArn" --output text)
  echo "Target Group ARN: $TARGET_GROUP_ARN"

  echo "Creating ALB Listener on port 80..."
  aws elbv2 create-listener \
    --load-balancer-arn "$ALB_ARN" \
    --protocol HTTP \
    --port 80 \
    --default-actions "Type=forward,TargetGroupArn=${TARGET_GROUP_ARN}" \
    --region "$AWS_REGION" >/dev/null
  echo "ALB Listener created."
fi

# ---------------------------------------------------------------------------
# Prepare task definition (replace placeholders in a temp copy)
# ---------------------------------------------------------------------------
echo ""
echo "Preparing task definition..."
TASK_DEF_TMP=$(mktemp /tmp/task-definition-XXXXXX.json)
cp "$TASK_DEF_FILE" "$TASK_DEF_TMP"

sed -i "s|{{ACCOUNT_ID}}|${ACCOUNT_ID}|g"     "$TASK_DEF_TMP"
sed -i "s|{{IMAGE_URI}}|${IMAGE_URI}|g"         "$TASK_DEF_TMP"
sed -i "s|{{AWS_REGION}}|${AWS_REGION}|g"       "$TASK_DEF_TMP"
sed -i "s|{{REDIS_HOST}}|${REDIS_HOST}|g"       "$TASK_DEF_TMP"

# ---------------------------------------------------------------------------
# Register task definition
# ---------------------------------------------------------------------------
echo "Registering task definition..."
TASK_DEF_ARN=$(aws ecs register-task-definition \
  --cli-input-json "file://${TASK_DEF_TMP}" \
  --region "$AWS_REGION" \
  --query "taskDefinition.taskDefinitionArn" --output text)
echo "Task Definition ARN: $TASK_DEF_ARN"
rm -f "$TASK_DEF_TMP"

# ---------------------------------------------------------------------------
# Prepare service definition (replace placeholders in a temp copy)
# ---------------------------------------------------------------------------
echo ""
echo "Preparing service definition..."
SERVICE_DEF_TMP=$(mktemp /tmp/service-definition-XXXXXX.json)
cp "$SERVICE_DEF_FILE" "$SERVICE_DEF_TMP"

sed -i "s|{{CLUSTER_NAME}}|${CLUSTER_NAME}|g"     "$SERVICE_DEF_TMP"
sed -i "s|{{SUBNET_1}}|${SUBNET_1}|g"             "$SERVICE_DEF_TMP"
sed -i "s|{{SUBNET_2}}|${SUBNET_2}|g"             "$SERVICE_DEF_TMP"
sed -i "s|{{SECURITY_GROUP}}|${SECURITY_GROUP}|g" "$SERVICE_DEF_TMP"

# ---------------------------------------------------------------------------
# Load balancer injection into service definition
# ---------------------------------------------------------------------------
SERVICE_NAME="resortslite-service"

if [[ "$NEED_LB" =~ ^[Yy]$ ]] && [ -n "$TARGET_GROUP_ARN" ]; then
  # Inject loadBalancers block using Python (available on most Linux systems)
  python3 - <<PYEOF
import json, sys

with open("${SERVICE_DEF_TMP}") as f:
    svc = json.load(f)

svc["loadBalancers"] = [{
    "targetGroupArn": "${TARGET_GROUP_ARN}",
    "containerName": "${PROJECT_NAME}",
    "containerPort": 8080
}]
svc["healthCheckGracePeriodSeconds"] = 300

with open("${SERVICE_DEF_TMP}", "w") as f:
    json.dump(svc, f, indent=2)
PYEOF
fi

# ---------------------------------------------------------------------------
# Create or update ECS service
# ---------------------------------------------------------------------------
echo ""
echo "Checking if ECS service exists: $SERVICE_NAME"
EXISTING_SERVICE=$(aws ecs describe-services \
  --cluster "$CLUSTER_NAME" \
  --services "$SERVICE_NAME" \
  --region "$AWS_REGION" \
  --query "services[?status=='ACTIVE'].serviceName" \
  --output text 2>/dev/null || echo "")

if [ -z "$EXISTING_SERVICE" ] || [ "$EXISTING_SERVICE" = "None" ]; then
  echo "Creating new ECS service: $SERVICE_NAME"
  aws ecs create-service \
    --cli-input-json "file://${SERVICE_DEF_TMP}" \
    --region "$AWS_REGION"
  echo "Service created."
else
  echo "Updating existing ECS service: $SERVICE_NAME"
  aws ecs update-service \
    --cluster "$CLUSTER_NAME" \
    --service "$SERVICE_NAME" \
    --task-definition "$TASK_DEF_ARN" \
    --region "$AWS_REGION" >/dev/null
  echo "Service updated."
fi

rm -f "$SERVICE_DEF_TMP"

# ---------------------------------------------------------------------------
# Wait for service stability
# ---------------------------------------------------------------------------
echo ""
echo "Waiting for service to stabilize (this may take a few minutes)..."
aws ecs wait services-stable \
  --cluster "$CLUSTER_NAME" \
  --services "$SERVICE_NAME" \
  --region "$AWS_REGION"
echo "Service is stable."

# ---------------------------------------------------------------------------
# Verify deployment
# ---------------------------------------------------------------------------
echo ""
echo "=============================================="
echo "  Deployment Summary"
echo "=============================================="
aws ecs describe-services \
  --cluster "$CLUSTER_NAME" \
  --services "$SERVICE_NAME" \
  --region "$AWS_REGION" \
  --query "services[0].{Status:status,Running:runningCount,Desired:desiredCount,TaskDef:taskDefinition}" \
  --output table

echo ""
echo "CloudWatch Log Group : $LOG_GROUP"
echo "ECS Cluster          : $CLUSTER_NAME"
echo "ECS Service          : $SERVICE_NAME"
echo "Task Definition ARN  : $TASK_DEF_ARN"

if [ -n "$ALB_DNS" ]; then
  echo "Load Balancer DNS    : http://$ALB_DNS"
  echo "Health Check URL     : http://$ALB_DNS/actuator/health"
fi

echo ""
echo "Deployment complete!"
echo ""
echo "Troubleshooting tips:"
echo "  - View logs  : aws logs tail $LOG_GROUP --follow --region $AWS_REGION"
echo "  - List tasks : aws ecs list-tasks --cluster $CLUSTER_NAME --service-name $SERVICE_NAME --region $AWS_REGION"
echo "  - Task detail: aws ecs describe-tasks --cluster $CLUSTER_NAME --tasks <TASK_ARN> --region $AWS_REGION"
