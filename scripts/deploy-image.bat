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
if "!AWS_REGION!"=="" (
    echo ERROR: AWS Region is required
    exit /b 1
)

set /p CLUSTER_NAME="Enter EKS Cluster Name: "
if "!CLUSTER_NAME!"=="" (
    echo ERROR: EKS Cluster Name is required
    exit /b 1
)

REM Prompt for Docker image URI
set /p IMAGE_URI="Enter Docker Image URI: "
if "!IMAGE_URI!"=="" (
    echo ERROR: Docker Image URI is required
    exit /b 1
)

echo.
echo === Application Configuration ===
echo The following environment variables will be configured for the application.
echo Press Enter to skip any optional configuration.
echo.

REM Database Configuration
set /p SPRING_DATASOURCE_URL="Enter Database URL (default: jdbc:h2:mem:resortdb): "
if "!SPRING_DATASOURCE_URL!"=="" set SPRING_DATASOURCE_URL=jdbc:h2:mem:resortdb;DB_CLOSE_DELAY=-1

set /p SPRING_DATASOURCE_USERNAME="Enter Database Username (default: sa): "
if "!SPRING_DATASOURCE_USERNAME!"=="" set SPRING_DATASOURCE_USERNAME=sa

set /p SPRING_DATASOURCE_PASSWORD="Enter Database Password (press Enter for empty): "
if "!SPRING_DATASOURCE_PASSWORD!"=="" set SPRING_DATASOURCE_PASSWORD=

REM Redis Configuration
set /p REDIS_HOST="Enter Redis Host (e.g., redis.example.com): "
if "!REDIS_HOST!"=="" set REDIS_HOST=localhost

set /p REDIS_PORT="Enter Redis Port (default: 6379): "
if "!REDIS_PORT!"=="" set REDIS_PORT=6379

set /p REDIS_PASSWORD="Enter Redis Password (press Enter for empty): "
if "!REDIS_PASSWORD!"=="" set REDIS_PASSWORD=

REM External Service Endpoints
set /p PAYMENT_API_URL="Enter Payment API URL (or press Enter to skip): "
if "!PAYMENT_API_URL!"=="" set PAYMENT_API_URL=http://payment-service:9090/payments/charge

set /p APP_PAYMENT_ENDPOINT="Enter Payment Endpoint (or press Enter to skip): "
if "!APP_PAYMENT_ENDPOINT!"=="" set APP_PAYMENT_ENDPOINT=http://payment-svc.internal:9090/charge

set /p APP_INVENTORY_ENDPOINT="Enter Inventory Endpoint (or press Enter to skip): "
if "!APP_INVENTORY_ENDPOINT!"=="" set APP_INVENTORY_ENDPOINT=http://inventory-svc.internal:8081/rooms

set /p APP_NOTIFICATION_ENDPOINT="Enter Notification Endpoint (or press Enter to skip): "
if "!APP_NOTIFICATION_ENDPOINT!"=="" set APP_NOTIFICATION_ENDPOINT=http://notify.internal:7070/send

echo.
echo ==========================================
echo Configuration Summary
echo ==========================================
echo AWS Region: !AWS_REGION!
echo EKS Cluster: !CLUSTER_NAME!
echo Docker Image: !IMAGE_URI!
echo Database URL: !SPRING_DATASOURCE_URL!
echo Redis Host: !REDIS_HOST!
echo ==========================================
echo.

set /p CONFIRM="Proceed with deployment? (yes/no): "
if not "!CONFIRM!"=="yes" (
    echo Deployment cancelled.
    exit /b 0
)

REM Configure kubectl for EKS
echo.
echo Configuring kubectl for EKS cluster...
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!

if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to configure kubectl for EKS cluster
    exit /b 1
)

REM Verify cluster connectivity
echo Verifying cluster connectivity...
kubectl cluster-info
if !ERRORLEVEL! neq 0 (
    echo ERROR: Cannot connect to Kubernetes cluster
    exit /b 1
)

echo.
echo Success: Connected to EKS cluster successfully

REM Create temporary directory for processed manifests
set TEMP_DIR=%TEMP%\k8s-deploy-%RANDOM%
mkdir "%TEMP_DIR%"

REM Copy manifests to temp directory
xcopy /E /I /Y kubernetes "%TEMP_DIR%" >nul

REM Replace placeholders in deployment.yaml using PowerShell
echo.
echo Updating Kubernetes manifests...
powershell -Command "(Get-Content '%TEMP_DIR%\deployment.yaml') -replace '{{IMAGE_URI}}', '%IMAGE_URI%' | Set-Content '%TEMP_DIR%\deployment.yaml'"
powershell -Command "(Get-Content '%TEMP_DIR%\deployment.yaml') -replace '{{SPRING_DATASOURCE_URL}}', '%SPRING_DATASOURCE_URL%' | Set-Content '%TEMP_DIR%\deployment.yaml'"
powershell -Command "(Get-Content '%TEMP_DIR%\deployment.yaml') -replace '{{SPRING_DATASOURCE_USERNAME}}', '%SPRING_DATASOURCE_USERNAME%' | Set-Content '%TEMP_DIR%\deployment.yaml'"
powershell -Command "(Get-Content '%TEMP_DIR%\deployment.yaml') -replace '{{SPRING_DATASOURCE_PASSWORD}}', '%SPRING_DATASOURCE_PASSWORD%' | Set-Content '%TEMP_DIR%\deployment.yaml'"
powershell -Command "(Get-Content '%TEMP_DIR%\deployment.yaml') -replace '{{REDIS_HOST}}', '%REDIS_HOST%' | Set-Content '%TEMP_DIR%\deployment.yaml'"
powershell -Command "(Get-Content '%TEMP_DIR%\deployment.yaml') -replace '{{REDIS_PORT}}', '%REDIS_PORT%' | Set-Content '%TEMP_DIR%\deployment.yaml'"
powershell -Command "(Get-Content '%TEMP_DIR%\deployment.yaml') -replace '{{REDIS_PASSWORD}}', '%REDIS_PASSWORD%' | Set-Content '%TEMP_DIR%\deployment.yaml'"
powershell -Command "(Get-Content '%TEMP_DIR%\deployment.yaml') -replace '{{PAYMENT_API_URL}}', '%PAYMENT_API_URL%' | Set-Content '%TEMP_DIR%\deployment.yaml'"
powershell -Command "(Get-Content '%TEMP_DIR%\deployment.yaml') -replace '{{APP_PAYMENT_ENDPOINT}}', '%APP_PAYMENT_ENDPOINT%' | Set-Content '%TEMP_DIR%\deployment.yaml'"
powershell -Command "(Get-Content '%TEMP_DIR%\deployment.yaml') -replace '{{APP_INVENTORY_ENDPOINT}}', '%APP_INVENTORY_ENDPOINT%' | Set-Content '%TEMP_DIR%\deployment.yaml'"
powershell -Command "(Get-Content '%TEMP_DIR%\deployment.yaml') -replace '{{APP_NOTIFICATION_ENDPOINT}}', '%APP_NOTIFICATION_ENDPOINT%' | Set-Content '%TEMP_DIR%\deployment.yaml'"

echo Success: Manifests updated successfully

REM Apply Kubernetes manifests
echo.
echo ==========================================
echo Deploying to Kubernetes...
echo ==========================================

echo.
echo Creating namespace...
kubectl apply -f "%TEMP_DIR%\namespace.yaml"

echo.
echo Deploying application...
kubectl apply -f "%TEMP_DIR%\deployment.yaml"

echo.
echo Creating service...
kubectl apply -f "%TEMP_DIR%\service.yaml"

echo.
echo Creating ingress...
kubectl apply -f "%TEMP_DIR%\ingress.yaml"

REM Wait for deployment rollout
echo.
echo Waiting for deployment to complete...
kubectl rollout status deployment/resortslite -n resortslite --timeout=5m

if !ERRORLEVEL! neq 0 (
    echo ERROR: Deployment rollout failed
    echo.
    echo Checking pod status...
    kubectl get pods -n resortslite
    echo.
    echo Checking pod logs...
    kubectl logs -n resortslite -l app=resortslite --tail=50
    rmdir /S /Q "%TEMP_DIR%"
    exit /b 1
)

REM Verify deployment
echo.
echo ==========================================
echo Verifying Deployment...
echo ==========================================
kubectl get pods,svc,ingress -n resortslite

REM Get ingress URL
echo.
echo ==========================================
echo Deployment Complete!
echo ==========================================
echo.
echo Application is being provisioned. Run the following command to get the URL:
echo kubectl get ingress resortslite-ingress -n resortslite
echo.
echo Useful commands:
echo   View pods:        kubectl get pods -n resortslite
echo   View logs:        kubectl logs -n resortslite -l app=resortslite
echo   Describe pod:     kubectl describe pod -n resortslite -l app=resortslite
echo   Scale deployment: kubectl scale deployment resortslite -n resortslite --replicas=3
echo   Delete deployment: kubectl delete namespace resortslite
echo.
echo ==========================================

REM Cleanup
rmdir /S /Q "%TEMP_DIR%"

endlocal
