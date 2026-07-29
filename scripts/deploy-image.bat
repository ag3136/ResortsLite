@echo off
setlocal enabledelayedexpansion

REM Deploy ResortsLite to AWS EKS (Windows)
REM This script configures kubectl and deploys the application to EKS

echo ==========================================
echo ResortsLite - AWS EKS Deployment Script
echo ==========================================
echo.

REM Prompt for AWS region
set /p AWS_REGION="Enter AWS Region (e.g., us-east-1): "
if "!AWS_REGION!"=="" (
    echo Error: AWS Region is required
    exit /b 1
)

REM Prompt for EKS cluster name
set /p CLUSTER_NAME="Enter EKS Cluster Name: "
if "!CLUSTER_NAME!"=="" (
    echo Error: EKS Cluster Name is required
    exit /b 1
)

REM Prompt for Docker image URI
set /p IMAGE_URI="Enter Docker Image URI (e.g., 123456789012.dkr.ecr.us-east-1.amazonaws.com/resortslite:latest): "
if "!IMAGE_URI!"=="" (
    echo Error: Docker Image URI is required
    exit /b 1
)

echo.
echo === Environment Configuration ===
echo The following environment variables are used by the application.
echo Press Enter to skip optional variables or provide values.
echo.

REM Prompt for database configuration
set /p SPRING_DATASOURCE_URL="Enter SPRING_DATASOURCE_URL (default: jdbc:h2:mem:resortdb): "
if "!SPRING_DATASOURCE_URL!"=="" set SPRING_DATASOURCE_URL=jdbc:h2:mem:resortdb;DB_CLOSE_DELAY=-1

set /p SPRING_DATASOURCE_USERNAME="Enter SPRING_DATASOURCE_USERNAME (default: sa): "
if "!SPRING_DATASOURCE_USERNAME!"=="" set SPRING_DATASOURCE_USERNAME=sa

set /p SPRING_DATASOURCE_PASSWORD="Enter SPRING_DATASOURCE_PASSWORD (default: empty): "
if "!SPRING_DATASOURCE_PASSWORD!"=="" set SPRING_DATASOURCE_PASSWORD=

REM Prompt for external service endpoints
set /p APP_PAYMENT_ENDPOINT="Enter APP_PAYMENT_ENDPOINT (default: http://payment-svc:9090/charge): "
if "!APP_PAYMENT_ENDPOINT!"=="" set APP_PAYMENT_ENDPOINT=http://payment-svc:9090/charge

set /p APP_INVENTORY_ENDPOINT="Enter APP_INVENTORY_ENDPOINT (default: http://inventory-svc:8081/rooms): "
if "!APP_INVENTORY_ENDPOINT!"=="" set APP_INVENTORY_ENDPOINT=http://inventory-svc:8081/rooms

set /p APP_NOTIFICATION_ENDPOINT="Enter APP_NOTIFICATION_ENDPOINT (default: http://notify-svc:7070/send): "
if "!APP_NOTIFICATION_ENDPOINT!"=="" set APP_NOTIFICATION_ENDPOINT=http://notify-svc:7070/send

echo.
echo ==========================================
echo Configuring kubectl for EKS
echo ==========================================
echo.

REM Configure kubectl to use EKS cluster
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!

if !ERRORLEVEL! neq 0 (
    echo Error: Failed to configure kubectl for EKS cluster
    exit /b 1
)

echo kubectl configured successfully

REM Verify cluster connectivity
echo.
echo Verifying cluster connectivity...
kubectl cluster-info

if !ERRORLEVEL! neq 0 (
    echo Error: Cannot connect to EKS cluster
    exit /b 1
)

echo.
echo ==========================================
echo Updating Kubernetes Manifests
echo ==========================================
echo.

REM Create temporary directory for processed manifests
set TEMP_DIR=%TEMP%\resortslite-deploy-%RANDOM%
mkdir !TEMP_DIR!
echo Using temporary directory: !TEMP_DIR!

REM Copy manifests to temp directory
xcopy /E /I /Y kubernetes !TEMP_DIR! >nul

REM Replace placeholders in deployment.yaml using PowerShell
echo Updating deployment.yaml with image URI and environment variables...

powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{IMAGE_URI}}', '!IMAGE_URI!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{SPRING_DATASOURCE_URL}}', '!SPRING_DATASOURCE_URL!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{SPRING_DATASOURCE_USERNAME}}', '!SPRING_DATASOURCE_USERNAME!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{SPRING_DATASOURCE_PASSWORD}}', '!SPRING_DATASOURCE_PASSWORD!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{APP_PAYMENT_ENDPOINT}}', '!APP_PAYMENT_ENDPOINT!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{APP_INVENTORY_ENDPOINT}}', '!APP_INVENTORY_ENDPOINT!' | Set-Content '!TEMP_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!TEMP_DIR!\deployment.yaml') -replace '{{APP_NOTIFICATION_ENDPOINT}}', '!APP_NOTIFICATION_ENDPOINT!' | Set-Content '!TEMP_DIR!\deployment.yaml'"

echo Manifests updated successfully

echo.
echo ==========================================
echo Deploying to AWS EKS
echo ==========================================
echo.

REM Apply namespace
echo Creating namespace...
kubectl apply -f !TEMP_DIR!\namespace.yaml

if !ERRORLEVEL! neq 0 (
    echo Error: Failed to create namespace
    rmdir /S /Q !TEMP_DIR!
    exit /b 1
)

REM Apply deployment
echo.
echo Creating deployment...
kubectl apply -f !TEMP_DIR!\deployment.yaml

if !ERRORLEVEL! neq 0 (
    echo Error: Failed to create deployment
    rmdir /S /Q !TEMP_DIR!
    exit /b 1
)

REM Apply service
echo.
echo Creating service...
kubectl apply -f !TEMP_DIR!\service.yaml

if !ERRORLEVEL! neq 0 (
    echo Error: Failed to create service
    rmdir /S /Q !TEMP_DIR!
    exit /b 1
)

REM Apply ingress
echo.
echo Creating ingress...
kubectl apply -f !TEMP_DIR!\ingress.yaml

if !ERRORLEVEL! neq 0 (
    echo Error: Failed to create ingress
    rmdir /S /Q !TEMP_DIR!
    exit /b 1
)

REM Clean up temporary directory
rmdir /S /Q !TEMP_DIR!

echo.
echo ==========================================
echo Waiting for Deployment Rollout
echo ==========================================
echo.

REM Wait for deployment to complete
kubectl rollout status deployment/resortslite -n resortslite --timeout=5m

if !ERRORLEVEL! neq 0 (
    echo Error: Deployment rollout failed or timed out
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
echo Deployment Verification
echo ==========================================
echo.

REM Display deployed resources
echo Pods:
kubectl get pods -n resortslite -o wide

echo.
echo Services:
kubectl get svc -n resortslite

echo.
echo Ingress:
kubectl get ingress -n resortslite

echo.
echo ==========================================
echo Deployment Completed Successfully
echo ==========================================
echo.

REM Get ingress URL
for /f "delims=" %%i in ('kubectl get ingress resortslite-ingress -n resortslite -o jsonpath^="{.status.loadBalancer.ingress[0].hostname}" 2^>nul') do set INGRESS_URL=%%i

if not "!INGRESS_URL!"=="" (
    echo Application URL: http://!INGRESS_URL!
    echo Health Check: http://!INGRESS_URL!/actuator/health
) else (
    echo Ingress URL not yet available. Run the following command to check:
    echo kubectl get ingress resortslite-ingress -n resortslite
)

echo.
echo Useful commands:
echo   View pods:        kubectl get pods -n resortslite
echo   View logs:        kubectl logs -n resortslite -l app=resortslite
echo   Describe pod:     kubectl describe pod ^<pod-name^> -n resortslite
echo   Scale deployment: kubectl scale deployment resortslite -n resortslite --replicas=3
echo   Delete deployment: kubectl delete namespace resortslite
echo.

endlocal
