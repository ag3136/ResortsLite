@echo off
setlocal enabledelayedexpansion

:: ============================================================
:: deploy-image.bat — Deploy ResortsLite to Azure AKS
:: Usage: scripts\deploy-image.bat
:: Run from repository root directory
:: Prerequisites: azure-cli, kubectl
:: ============================================================

set APP_NAME=resortslite
set NAMESPACE=resortslite
set MANIFESTS_DIR=kubernetes
set TEMP_DIR=%TEMP%\resortslite-k8s-deploy

echo ============================================
echo   ResortsLite — Deploy to Azure AKS
echo ============================================

:: ---- Azure / AKS credentials ----
set /p RESOURCE_GROUP="Enter Azure Resource Group name: "
if "!RESOURCE_GROUP!"=="" (
    echo ERROR: Resource group cannot be empty.
    exit /b 1
)

set /p CLUSTER_NAME="Enter AKS Cluster name: "
if "!CLUSTER_NAME!"=="" (
    echo ERROR: AKS cluster name cannot be empty.
    exit /b 1
)

:: ---- Docker image URI ----
set /p IMAGE_URI="Enter full Docker image URI (e.g. myregistry.azurecr.io/resortslite:latest): "
if "!IMAGE_URI!"=="" (
    echo ERROR: Image URI cannot be empty.
    exit /b 1
)

:: ---- Application environment variables ----
echo.
echo --- Application Environment Variables ---
echo (Press Enter to skip any variable and keep the default)

set /p REDIS_HOST="Enter REDIS_HOST (Azure Cache for Redis hostname) [localhost]: "
if "!REDIS_HOST!"=="" set REDIS_HOST=localhost

set /p REDIS_PORT="Enter REDIS_PORT [6379]: "
if "!REDIS_PORT!"=="" set REDIS_PORT=6379

set /p REDIS_PASSWORD="Enter REDIS_PASSWORD (leave blank if none): "

set /p REDIS_SSL="Enter REDIS_SSL [false]: "
if "!REDIS_SSL!"=="" set REDIS_SSL=false

set /p APP_PAYMENT_ENDPOINT="Enter APP_PAYMENT_ENDPOINT [http://payment-svc:9090/charge]: "
if "!APP_PAYMENT_ENDPOINT!"=="" set APP_PAYMENT_ENDPOINT=http://payment-svc:9090/charge

set /p APP_INVENTORY_ENDPOINT="Enter APP_INVENTORY_ENDPOINT [http://inventory-svc:8081/rooms]: "
if "!APP_INVENTORY_ENDPOINT!"=="" set APP_INVENTORY_ENDPOINT=http://inventory-svc:8081/rooms

set /p APP_NOTIFICATION_ENDPOINT="Enter APP_NOTIFICATION_ENDPOINT [http://notify-svc:7070/send]: "
if "!APP_NOTIFICATION_ENDPOINT!"=="" set APP_NOTIFICATION_ENDPOINT=http://notify-svc:7070/send

:: ---- Configure kubectl for AKS ----
echo.
echo Configuring kubectl for AKS cluster: !CLUSTER_NAME! ...
az aks get-credentials --resource-group !RESOURCE_GROUP! --name !CLUSTER_NAME! --overwrite-existing
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to get AKS credentials.
    exit /b 1
)

echo Verifying cluster connectivity ...
kubectl cluster-info
if !ERRORLEVEL! neq 0 (
    echo ERROR: Cannot connect to AKS cluster.
    exit /b 1
)

:: ---- Copy manifests to temp directory ----
echo.
echo Updating Kubernetes manifests with deployment values ...
if exist "!TEMP_DIR!" rmdir /s /q "!TEMP_DIR!"
xcopy /s /e /i /q "!MANIFESTS_DIR!" "!TEMP_DIR!" >nul
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to copy manifests.
    exit /b 1
)

:: ---- Patch deployment.yaml using PowerShell ----
powershell -NoProfile -Command ^
  "(Get-Content '!TEMP_DIR!\deployment.yaml') ^
   -replace '{{IMAGE_URI}}','!IMAGE_URI!' ^
   -replace '{{REDIS_HOST}}','!REDIS_HOST!' ^
   -replace '{{REDIS_PORT}}','!REDIS_PORT!' ^
   -replace '{{REDIS_PASSWORD}}','!REDIS_PASSWORD!' ^
   -replace '{{REDIS_SSL}}','!REDIS_SSL!' ^
   -replace '{{APP_PAYMENT_ENDPOINT}}','!APP_PAYMENT_ENDPOINT!' ^
   -replace '{{APP_INVENTORY_ENDPOINT}}','!APP_INVENTORY_ENDPOINT!' ^
   -replace '{{APP_NOTIFICATION_ENDPOINT}}','!APP_NOTIFICATION_ENDPOINT!' ^
   | Set-Content '!TEMP_DIR!\deployment.yaml'"
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to update deployment manifest.
    exit /b 1
)

:: ---- Apply manifests in order ----
echo.
echo Applying Kubernetes manifests ...

echo   [1/4] Applying namespace ...
kubectl apply -f "!TEMP_DIR!\namespace.yaml"
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply namespace. & exit /b 1 )

echo   [2/4] Applying deployment ...
kubectl apply -f "!TEMP_DIR!\deployment.yaml"
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply deployment. & exit /b 1 )

echo   [3/4] Applying service ...
kubectl apply -f "!TEMP_DIR!\service.yaml"
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply service. & exit /b 1 )

echo   [4/4] Applying ingress ...
kubectl apply -f "!TEMP_DIR!\ingress.yaml"
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply ingress. & exit /b 1 )

:: ---- Wait for rollout ----
echo.
echo Waiting for deployment rollout ...
kubectl rollout status deployment/!APP_NAME! -n !NAMESPACE! --timeout=300s
if !ERRORLEVEL! neq 0 (
    echo WARNING: Rollout did not complete within timeout. Check pod status manually.
)

:: ---- Verify resources ----
echo.
echo Verifying deployed resources ...
kubectl get pods,svc,ingress -n !NAMESPACE!

echo.
echo ============================================
echo   Deployment Complete!
echo   Namespace : !NAMESPACE!
echo   Image     : !IMAGE_URI!
echo   App URL   : http://resortslite.example.com
echo   (Update DNS to point to ingress IP)
echo ============================================
echo.
echo Rollback command (if needed):
echo   kubectl rollout undo deployment/!APP_NAME! -n !NAMESPACE!

:: ---- Cleanup temp files ----
if exist "!TEMP_DIR!" rmdir /s /q "!TEMP_DIR!"

endlocal
exit /b 0
