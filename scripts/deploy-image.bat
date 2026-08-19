@echo off
setlocal enabledelayedexpansion

REM ============================================
REM Deploy to AWS EKS Script (Windows)
REM For ResortsLite Spring Boot Application
REM ============================================

echo ==========================================
echo AWS EKS Deployment Script
echo ==========================================
echo.

REM Prompt for AWS configuration
set /p AWS_REGION="Enter AWS Region (e.g., us-east-1): "
set /p CLUSTER_NAME="Enter EKS Cluster Name: "

echo.
echo AWS Region: !AWS_REGION!
echo EKS Cluster: !CLUSTER_NAME!
echo.

REM Prompt for Docker image URI
set /p IMAGE_URI="Enter Docker Image URI (e.g., 123456789012.dkr.ecr.us-east-1.amazonaws.com/resortslite:latest): "

echo.
echo Docker Image: !IMAGE_URI!
echo.

REM Prompt for environment variables
echo ==========================================
echo Environment Configuration
echo ==========================================
echo Enter values for environment variables (or press Enter to skip):
echo.

set /p SPRING_DATASOURCE_URL="SPRING_DATASOURCE_URL (default: jdbc:h2:mem:resortdb): "
if "!SPRING_DATASOURCE_URL!"=="" set SPRING_DATASOURCE_URL=jdbc:h2:mem:resortdb;DB_CLOSE_DELAY=-1

set /p SPRING_DATASOURCE_USERNAME="SPRING_DATASOURCE_USERNAME (default: sa): "
if "!SPRING_DATASOURCE_USERNAME!"=="" set SPRING_DATASOURCE_USERNAME=sa

set /p SPRING_DATASOURCE_PASSWORD="SPRING_DATASOURCE_PASSWORD (default: empty): "
if "!SPRING_DATASOURCE_PASSWORD!"=="" set SPRING_DATASOURCE_PASSWORD=

set /p REDIS_HOST="REDIS_HOST (e.g., redis.example.com): "
if "!REDIS_HOST!"=="" set REDIS_HOST=redis.example.com

set /p REDIS_PORT="REDIS_PORT (default: 6379): "
if "!REDIS_PORT!"=="" set REDIS_PORT=6379

set /p REDIS_PASSWORD="REDIS_PASSWORD (default: empty): "
if "!REDIS_PASSWORD!"=="" set REDIS_PASSWORD=

set /p PAYMENT_SERVICE_URL="PAYMENT_SERVICE_URL (default: http://payment-service:9090/payments/charge): "
if "!PAYMENT_SERVICE_URL!"=="" set PAYMENT_SERVICE_URL=http://payment-service:9090/payments/charge

set /p APP_PAYMENT_ENDPOINT="APP_PAYMENT_ENDPOINT (default: http://payment-svc.internal:9090/charge): "
if "!APP_PAYMENT_ENDPOINT!"=="" set APP_PAYMENT_ENDPOINT=http://payment-svc.internal:9090/charge

set /p APP_INVENTORY_ENDPOINT="APP_INVENTORY_ENDPOINT (default: http://inventory-svc.internal:8081/rooms): "
if "!APP_INVENTORY_ENDPOINT!"=="" set APP_INVENTORY_ENDPOINT=http://inventory-svc.internal:8081/rooms

set /p APP_NOTIFICATION_ENDPOINT="APP_NOTIFICATION_ENDPOINT (default: http://notify.internal:7070/send): "
if "!APP_NOTIFICATION_ENDPOINT!"=="" set APP_NOTIFICATION_ENDPOINT=http://notify.internal:7070/send

echo.
echo ==========================================
echo Configuring kubectl for EKS...
echo ==========================================

REM Configure kubectl to use EKS cluster
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!

if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to configure kubectl for EKS cluster
    exit /b 1
)

echo kubectl configured successfully
echo.

REM Verify cluster connectivity
echo Verifying cluster connectivity...
kubectl cluster-info
if !ERRORLEVEL! neq 0 (
    echo ERROR: Cannot connect to Kubernetes cluster
    exit /b 1
)

echo.
echo ==========================================
echo Updating Kubernetes Manifests...
echo ==========================================

REM Create temporary directory for updated manifests
set TEMP_DIR=%TEMP%\k8s-deploy-%RANDOM%
mkdir !TEMP_DIR!
xcopy /E /I /Q kubernetes !TEMP_DIR! >nul

REM Update deployment.yaml with image URI and environment variables
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{IMAGE_URI}}', '!IMAGE_URI!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{SPRING_DATASOURCE_URL}}', '!SPRING_DATASOURCE_URL!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{SPRING_DATASOURCE_USERNAME}}', '!SPRING_DATASOURCE_USERNAME!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{SPRING_DATASOURCE_PASSWORD}}', '!SPRING_DATASOURCE_PASSWORD!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{REDIS_HOST}}', '!REDIS_HOST!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{REDIS_PORT}}', '!REDIS_PORT!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{REDIS_PASSWORD}}', '!REDIS_PASSWORD!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{PAYMENT_SERVICE_URL}}', '!PAYMENT_SERVICE_URL!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{APP_PAYMENT_ENDPOINT}}', '!APP_PAYMENT_ENDPOINT!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{APP_INVENTORY_ENDPOINT}}', '!APP_INVENTORY_ENDPOINT!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{APP_NOTIFICATION_ENDPOINT}}', '!APP_NOTIFICATION_ENDPOINT!' | Set-Content '!TEMP_DIR!\deployment.yaml'"

echo Manifests updated successfully
echo.

REM Apply Kubernetes manifests
echo ==========================================
echo Deploying to Kubernetes...
echo ==========================================

echo Creating namespace...
kubectl apply -f !TEMP_DIR!\namespace.yaml

echo.
echo Deploying application...
kubectl apply -f !TEMP_DIR!\deployment.yaml

echo.
echo Creating service...
kubectl apply -f !TEMP_DIR!\service.yaml

echo.
echo Creating ingress...
kubectl apply -f !TEMP_DIR!\ingress.yaml

echo.
echo ==========================================
echo Waiting for Deployment Rollout...
echo ==========================================

kubectl rollout status deployment/resortslite -n resortslite --timeout=5m

if !ERRORLEVEL! neq 0 (
    echo ERROR: Deployment rollout failed
    echo.
    echo Checking pod status...
    kubectl get pods -n resortslite
    echo.
    echo Checking pod logs...
    kubectl logs -n resortslite -l app=resortslite --tail=50
    exit /b 1
)

echo.
echo ==========================================
echo Deployment Successful!
echo ==========================================

REM Display deployment information
echo.
echo Deployment Information:
kubectl get pods,svc,ingress -n resortslite

echo.
echo ==========================================
echo Application Access Information
echo ==========================================

REM Get ingress URL
for /f "delims=" %%i in ('kubectl get ingress resortslite-ingress -n resortslite -o jsonpath^="{.status.loadBalancer.ingress[0].hostname}" 2^>nul') do set INGRESS_URL=%%i
if "!INGRESS_URL!"=="" set INGRESS_URL=Pending...

echo Ingress URL: !INGRESS_URL!
echo.
echo Note: It may take a few minutes for the Load Balancer to become available.
echo You can check the status with: kubectl get ingress -n resortslite
echo.

REM Cleanup temporary directory
rmdir /S /Q !TEMP_DIR!

echo ==========================================
echo Deployment Complete!
echo ==========================================
echo.
echo Useful Commands:
echo   View pods:        kubectl get pods -n resortslite
echo   View logs:        kubectl logs -n resortslite -l app=resortslite
echo   View services:    kubectl get svc -n resortslite
echo   View ingress:     kubectl get ingress -n resortslite
echo   Scale deployment: kubectl scale deployment resortslite -n resortslite --replicas=3
echo   Rollback:         kubectl rollout undo deployment/resortslite -n resortslite
echo ==========================================

endlocal
