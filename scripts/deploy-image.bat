@echo off
setlocal enabledelayedexpansion

rem ============================================================
rem deploy-image.bat — Deploy BRDresort to Azure AKS (Windows)
rem ============================================================

set "APP_NAME=brdresort"
set "NAMESPACE=brdresort"

echo ============================================
echo   BRDresort - Deploy to Azure AKS
echo ============================================
echo.

rem ---- Azure / AKS credentials ----
set /p "RESOURCE_GROUP=Enter Azure Resource Group name: "
if "!RESOURCE_GROUP!"=="" (
    echo ERROR: Resource group cannot be empty.
    exit /b 1
)

set /p "CLUSTER_NAME=Enter AKS Cluster name: "
if "!CLUSTER_NAME!"=="" (
    echo ERROR: AKS cluster name cannot be empty.
    exit /b 1
)

rem ---- Docker image URI ----
set /p "IMAGE_URI=Enter full Docker image URI (e.g. myregistry.azurecr.io/brdresort:latest): "
if "!IMAGE_URI!"=="" (
    echo ERROR: Image URI cannot be empty.
    exit /b 1
)

echo.
echo ---- Application Environment Variables ----
echo Press Enter to keep the default value.
echo.

set /p "REDIS_HOST_VAL=Enter REDIS_HOST [redis-service]: "
if "!REDIS_HOST_VAL!"=="" set "REDIS_HOST_VAL=redis-service"

set /p "REDIS_PORT_VAL=Enter REDIS_PORT [6379]: "
if "!REDIS_PORT_VAL!"=="" set "REDIS_PORT_VAL=6379"

set /p "REDIS_PASSWORD_VAL=Enter REDIS_PASSWORD (leave blank if none): "

set /p "AZURE_BLOB_REPORTS_URL_VAL=Enter AZURE_BLOB_REPORTS_URL: "
if "!AZURE_BLOB_REPORTS_URL_VAL!"=="" set "AZURE_BLOB_REPORTS_URL_VAL=https://storageaccount.blob.core.windows.net/reports"

set /p "AZURE_BLOB_BACKUP_URL_VAL=Enter AZURE_BLOB_BACKUP_URL: "
if "!AZURE_BLOB_BACKUP_URL_VAL!"=="" set "AZURE_BLOB_BACKUP_URL_VAL=https://storageaccount.blob.core.windows.net/backups"

set /p "PAYMENT_API_URL_VAL=Enter PAYMENT_API_URL: "
if "!PAYMENT_API_URL_VAL!"=="" set "PAYMENT_API_URL_VAL=http://payment-service/payments/charge"

rem ---- Substitute placeholders using PowerShell ----
echo.
echo Substituting placeholders in Kubernetes manifests ...

set "K8S_DIR=%~dp0..\kubernetes"
set "DEPLOY_YAML=!K8S_DIR!\deployment.yaml"
set "DEPLOY_YAML_TMP=!K8S_DIR!\deployment.yaml.tmp"

copy /Y "!DEPLOY_YAML!" "!DEPLOY_YAML_TMP!" >nul

powershell -Command ^
  "(Get-Content '!DEPLOY_YAML_TMP!') ^
   -replace '{{IMAGE_URI}}','!IMAGE_URI!' ^
   -replace '{{REDIS_HOST}}','!REDIS_HOST_VAL!' ^
   -replace '{{REDIS_PORT}}','!REDIS_PORT_VAL!' ^
   -replace '{{REDIS_PASSWORD}}','!REDIS_PASSWORD_VAL!' ^
   -replace '{{AZURE_BLOB_REPORTS_URL}}','!AZURE_BLOB_REPORTS_URL_VAL!' ^
   -replace '{{AZURE_BLOB_BACKUP_URL}}','!AZURE_BLOB_BACKUP_URL_VAL!' ^
   -replace '{{PAYMENT_API_URL}}','!PAYMENT_API_URL_VAL!' ^
   | Set-Content '!DEPLOY_YAML_TMP!'"
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to substitute placeholders.
    exit /b 1
)

rem ---- Configure kubectl for AKS ----
echo.
echo Configuring kubectl for AKS cluster: !CLUSTER_NAME! ...
az aks get-credentials --resource-group "!RESOURCE_GROUP!" --name "!CLUSTER_NAME!" --overwrite-existing
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to get AKS credentials.
    exit /b 1
)

echo.
echo Verifying cluster connectivity ...
kubectl cluster-info
if !ERRORLEVEL! neq 0 (
    echo ERROR: Cannot connect to AKS cluster.
    exit /b 1
)

rem ---- Apply manifests ----
echo.
echo Applying Kubernetes manifests ...

echo   [1/4] Applying namespace ...
kubectl apply -f "!K8S_DIR!\namespace.yaml"
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply namespace. & exit /b 1 )

echo   [2/4] Applying deployment ...
kubectl apply -f "!DEPLOY_YAML_TMP!"
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply deployment. & exit /b 1 )

echo   [3/4] Applying service ...
kubectl apply -f "!K8S_DIR!\service.yaml"
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply service. & exit /b 1 )

echo   [4/4] Applying ingress ...
kubectl apply -f "!K8S_DIR!\ingress.yaml"
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply ingress. & exit /b 1 )

rem Clean up temp file
del /f /q "!DEPLOY_YAML_TMP!" >nul 2>&1

rem ---- Wait for rollout ----
echo.
echo Waiting for deployment rollout ...
kubectl rollout status deployment/!APP_NAME! -n !NAMESPACE! --timeout=300s
if !ERRORLEVEL! neq 0 (
    echo ERROR: Deployment rollout failed.
    echo Rollback command: kubectl rollout undo deployment/!APP_NAME! -n !NAMESPACE!
    exit /b 1
)

rem ---- Verify resources ----
echo.
echo Verifying deployed resources ...
kubectl get pods,svc,ingress -n !NAMESPACE!

echo.
echo ============================================
echo   Deployment Complete!
echo   Namespace : !NAMESPACE!
echo   Image     : !IMAGE_URI!
echo   App URL   : http://brdresort.example.com
echo   (Update DNS to point to ingress IP)
echo ============================================
echo.
echo Rollback command (if needed):
echo   kubectl rollout undo deployment/!APP_NAME! -n !NAMESPACE!

endlocal
