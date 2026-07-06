@echo off
setlocal enabledelayedexpansion

:: =============================================================================
:: deploy-image.bat — Deploy ResortsLite to AWS ECS Fargate (Windows)
:: Usage: scripts\deploy-image.bat
:: Run from the repository root directory
:: =============================================================================

set PROJECT_NAME=resortslite
set TASK_DEF_FILE=ecs\task-definition.json
set SERVICE_DEF_FILE=ecs\service-definition.json
set LOG_GROUP=/ecs/resortslite
set SERVICE_NAME=resortslite-service

echo ==============================================
echo   ResortsLite -- ECS Fargate Deployment
echo ==============================================
echo.

:: ---------------------------------------------------------------------------
:: Collect configuration
:: ---------------------------------------------------------------------------
set /p AWS_REGION="Enter AWS region [us-east-1]: "
if "!AWS_REGION!"=="" set AWS_REGION=us-east-1

set /p CLUSTER_NAME="Enter ECS cluster name [resortslite-cluster]: "
if "!CLUSTER_NAME!"=="" set CLUSTER_NAME=resortslite-cluster

set /p IMAGE_URI="Enter ECR image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/resortslite:latest): "
if "!IMAGE_URI!"=="" (
    echo ERROR: Image URI is required.
    exit /b 1
)

set /p SUBNET_1="Enter Subnet 1 ID (e.g. subnet-xxxxxxxx): "
set /p SUBNET_2="Enter Subnet 2 ID (e.g. subnet-yyyyyyyy): "
set /p SECURITY_GROUP="Enter Security Group ID (e.g. sg-xxxxxxxx): "
set /p REDIS_HOST="Enter Redis host (ElastiCache endpoint) [localhost]: "
if "!REDIS_HOST!"=="" set REDIS_HOST=localhost

:: ---------------------------------------------------------------------------
:: Auto-detect AWS Account ID
:: ---------------------------------------------------------------------------
echo.
echo Detecting AWS Account ID...
for /f "tokens=*" %%i in ('aws sts get-caller-identity --query Account --output text') do set ACCOUNT_ID=%%i
echo Account ID: !ACCOUNT_ID!

:: ---------------------------------------------------------------------------
:: Ensure CloudWatch log group exists
:: ---------------------------------------------------------------------------
echo.
echo Ensuring CloudWatch log group exists: !LOG_GROUP!
aws logs describe-log-groups --log-group-name-prefix "!LOG_GROUP!" --region !AWS_REGION! --query "logGroups[0].logGroupName" --output text >nul 2>&1
if !ERRORLEVEL! neq 0 (
    aws logs create-log-group --log-group-name "!LOG_GROUP!" --region !AWS_REGION!
)
echo Log group ready: !LOG_GROUP!

:: ---------------------------------------------------------------------------
:: Ensure ECS cluster exists
:: ---------------------------------------------------------------------------
echo.
echo Checking ECS cluster: !CLUSTER_NAME!
aws ecs describe-clusters --clusters !CLUSTER_NAME! --region !AWS_REGION! --query "clusters[0].status" --output text >nul 2>&1
if !ERRORLEVEL! neq 0 (
    echo Creating ECS cluster: !CLUSTER_NAME!
    aws ecs create-cluster --cluster-name !CLUSTER_NAME! --region !AWS_REGION!
)
echo Cluster ready: !CLUSTER_NAME!

:: ---------------------------------------------------------------------------
:: Load balancer prompt
:: ---------------------------------------------------------------------------
echo.
set /p NEED_LB="Do you need an Application Load Balancer for this service? (y/n) [n]: "
if "!NEED_LB!"=="" set NEED_LB=n

set TARGET_GROUP_ARN=
set ALB_DNS=

if /i "!NEED_LB!"=="y" (
    set /p VPC_ID="Enter VPC ID for the ALB (e.g. vpc-xxxxxxxx): "

    echo.
    echo Creating Application Load Balancer...
    for /f "tokens=*" %%i in ('aws elbv2 create-load-balancer --name !PROJECT_NAME!-alb --subnets !SUBNET_1! !SUBNET_2! --security-groups !SECURITY_GROUP! --scheme internet-facing --type application --region !AWS_REGION! --query "LoadBalancers[0].LoadBalancerArn" --output text') do set ALB_ARN=%%i
    echo ALB ARN: !ALB_ARN!

    for /f "tokens=*" %%i in ('aws elbv2 describe-load-balancers --load-balancer-arns !ALB_ARN! --region !AWS_REGION! --query "LoadBalancers[0].DNSName" --output text') do set ALB_DNS=%%i

    echo Creating Target Group...
    for /f "tokens=*" %%i in ('aws elbv2 create-target-group --name !PROJECT_NAME!-tg --protocol HTTP --port 8080 --vpc-id !VPC_ID! --target-type ip --health-check-path /actuator/health --health-check-interval-seconds 30 --healthy-threshold-count 2 --unhealthy-threshold-count 3 --region !AWS_REGION! --query "TargetGroups[0].TargetGroupArn" --output text') do set TARGET_GROUP_ARN=%%i
    echo Target Group ARN: !TARGET_GROUP_ARN!

    echo Creating ALB Listener on port 80...
    aws elbv2 create-listener --load-balancer-arn !ALB_ARN! --protocol HTTP --port 80 --default-actions "Type=forward,TargetGroupArn=!TARGET_GROUP_ARN!" --region !AWS_REGION! >nul
    echo ALB Listener created.
)

:: ---------------------------------------------------------------------------
:: Prepare task definition (replace placeholders using PowerShell)
:: ---------------------------------------------------------------------------
echo.
echo Preparing task definition...
set TASK_DEF_TMP=%TEMP%\task-definition-tmp.json
copy /y !TASK_DEF_FILE! !TASK_DEF_TMP! >nul

powershell -Command "(Get-Content '!TASK_DEF_TMP!') -replace '{{ACCOUNT_ID}}','!ACCOUNT_ID!' -replace '{{IMAGE_URI}}','!IMAGE_URI!' -replace '{{AWS_REGION}}','!AWS_REGION!' -replace '{{REDIS_HOST}}','!REDIS_HOST!' | Set-Content '!TASK_DEF_TMP!'"

:: ---------------------------------------------------------------------------
:: Register task definition
:: ---------------------------------------------------------------------------
echo Registering task definition...
for /f "tokens=*" %%i in ('aws ecs register-task-definition --cli-input-json file://!TASK_DEF_TMP! --region !AWS_REGION! --query "taskDefinition.taskDefinitionArn" --output text') do set TASK_DEF_ARN=%%i
echo Task Definition ARN: !TASK_DEF_ARN!
del /f /q !TASK_DEF_TMP! >nul 2>&1

:: ---------------------------------------------------------------------------
:: Prepare service definition
:: ---------------------------------------------------------------------------
echo.
echo Preparing service definition...
set SERVICE_DEF_TMP=%TEMP%\service-definition-tmp.json
copy /y !SERVICE_DEF_FILE! !SERVICE_DEF_TMP! >nul

powershell -Command "(Get-Content '!SERVICE_DEF_TMP!') -replace '{{CLUSTER_NAME}}','!CLUSTER_NAME!' -replace '{{SUBNET_1}}','!SUBNET_1!' -replace '{{SUBNET_2}}','!SUBNET_2!' -replace '{{SECURITY_GROUP}}','!SECURITY_GROUP!' | Set-Content '!SERVICE_DEF_TMP!'"

:: ---------------------------------------------------------------------------
:: Inject load balancer into service definition if needed
:: ---------------------------------------------------------------------------
if /i "!NEED_LB!"=="y" (
    if not "!TARGET_GROUP_ARN!"=="" (
        powershell -Command "$svc = Get-Content '!SERVICE_DEF_TMP!' | ConvertFrom-Json; $lb = @{targetGroupArn='!TARGET_GROUP_ARN!'; containerName='!PROJECT_NAME!'; containerPort=8080}; $svc | Add-Member -NotePropertyName loadBalancers -NotePropertyValue @($lb) -Force; $svc | Add-Member -NotePropertyName healthCheckGracePeriodSeconds -NotePropertyValue 300 -Force; $svc | ConvertTo-Json -Depth 10 | Set-Content '!SERVICE_DEF_TMP!'"
    )
)

:: ---------------------------------------------------------------------------
:: Create or update ECS service
:: ---------------------------------------------------------------------------
echo.
echo Checking if ECS service exists: !SERVICE_NAME!
for /f "tokens=*" %%i in ('aws ecs describe-services --cluster !CLUSTER_NAME! --services !SERVICE_NAME! --region !AWS_REGION! --query "services[?status==''ACTIVE''].serviceName" --output text 2^>nul') do set EXISTING_SERVICE=%%i

if "!EXISTING_SERVICE!"=="" (
    echo Creating new ECS service: !SERVICE_NAME!
    aws ecs create-service --cli-input-json file://!SERVICE_DEF_TMP! --region !AWS_REGION!
    if !ERRORLEVEL! neq 0 (
        echo Failed to create ECS service.
        del /f /q !SERVICE_DEF_TMP! >nul 2>&1
        exit /b 1
    )
    echo Service created.
) else (
    echo Updating existing ECS service: !SERVICE_NAME!
    aws ecs update-service --cluster !CLUSTER_NAME! --service !SERVICE_NAME! --task-definition !TASK_DEF_ARN! --region !AWS_REGION! >nul
    if !ERRORLEVEL! neq 0 (
        echo Failed to update ECS service.
        del /f /q !SERVICE_DEF_TMP! >nul 2>&1
        exit /b 1
    )
    echo Service updated.
)
del /f /q !SERVICE_DEF_TMP! >nul 2>&1

:: ---------------------------------------------------------------------------
:: Wait for service stability
:: ---------------------------------------------------------------------------
echo.
echo Waiting for service to stabilize (this may take a few minutes)...
aws ecs wait services-stable --cluster !CLUSTER_NAME! --services !SERVICE_NAME! --region !AWS_REGION!
if !ERRORLEVEL! neq 0 (
    echo WARNING: Service did not stabilize within the expected time.
    echo Check ECS console and CloudWatch logs for details.
)
echo Service is stable.

:: ---------------------------------------------------------------------------
:: Verify deployment
:: ---------------------------------------------------------------------------
echo.
echo ==============================================
echo   Deployment Summary
echo ==============================================
aws ecs describe-services --cluster !CLUSTER_NAME! --services !SERVICE_NAME! --region !AWS_REGION! --query "services[0].{Status:status,Running:runningCount,Desired:desiredCount}" --output table

echo.
echo CloudWatch Log Group : !LOG_GROUP!
echo ECS Cluster          : !CLUSTER_NAME!
echo ECS Service          : !SERVICE_NAME!
echo Task Definition ARN  : !TASK_DEF_ARN!

if not "!ALB_DNS!"=="" (
    echo Load Balancer DNS    : http://!ALB_DNS!
    echo Health Check URL     : http://!ALB_DNS!/actuator/health
)

echo.
echo Deployment complete!
echo.
echo Troubleshooting tips:
echo   View logs  : aws logs tail !LOG_GROUP! --follow --region !AWS_REGION!
echo   List tasks : aws ecs list-tasks --cluster !CLUSTER_NAME! --service-name !SERVICE_NAME! --region !AWS_REGION!

endlocal
