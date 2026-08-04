@echo off
setlocal enabledelayedexpansion

REM Deploy ResortsLite to GCP GKE
REM This script deploys the containerized application to Google Kubernetes Engine

echo ==========================================
echo ResortsLite - GKE Deployment Script
echo ==========================================
echo.

REM Prompt for GCP configuration
set /p GCP_PROJECT="Enter GCP Project ID: "
if "!GCP_PROJECT!"=="" (
    echo ERROR: GCP Project ID is required!
    exit /b 1
)

set /p GCP_ZONE="Enter GCP Zone (e.g., us-central1-a): "
if "!GCP_ZONE!"=="" (
    echo ERROR: GCP Zone is required!
    exit /b 1
)

set /p CLUSTER_NAME="Enter GKE Cluster Name: "
if "!CLUSTER_NAME!"=="" (
    echo ERROR: GKE Cluster Name is required!
    exit /b 1
)

set /p IMAGE_URI="Enter Docker Image URI (with tag): "
if "!IMAGE_URI!"=="" (
    echo ERROR: Docker Image URI is required!
    exit /b 1
)

echo.
echo === Application Configuration ===
set /p REDIS_HOST="Enter Redis Host (or press Enter to skip): "
if "!REDIS_HOST!"=="" set REDIS_HOST=localhost

set /p REDIS_PORT="Enter Redis Port (default: 6379): "
if "!REDIS_PORT!"=="" set REDIS_PORT=6379

set /p REDIS_PASSWORD="Enter Redis Password (or press Enter for none): "

set /p INVENTORY_SERVICE_URL="Enter Inventory Service URL (or press Enter for default): "
if "!INVENTORY_SERVICE_URL!"=="" set INVENTORY_SERVICE_URL=http://localhost:8080

echo.
echo ==========================================
echo Configuring kubectl for GKE...
echo ==========================================
call gcloud container clusters get-credentials "!CLUSTER_NAME!" --zone "!GCP_ZONE!" --project "!GCP_PROJECT!"
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to configure kubectl!
    exit /b 1
)

echo.
echo Verifying cluster connectivity...
kubectl cluster-info
if !ERRORLEVEL! neq 0 (
    echo ERROR: Cannot connect to cluster!
    exit /b 1
)

echo.
echo ==========================================
echo Updating Kubernetes manifests...
echo ==========================================

REM Update deployment.yaml with actual values using PowerShell
powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{IMAGE_URI}}', '!IMAGE_URI!' | Set-Content kubernetes\deployment.yaml"
powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{REDIS_HOST}}', '!REDIS_HOST!' | Set-Content kubernetes\deployment.yaml"
powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{REDIS_PORT}}', '!REDIS_PORT!' | Set-Content kubernetes\deployment.yaml"
powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{REDIS_PASSWORD}}', '!REDIS_PASSWORD!' | Set-Content kubernetes\deployment.yaml"
powershell -Command "(Get-Content kubernetes\deployment.yaml) -replace '{{INVENTORY_SERVICE_URL}}', '!INVENTORY_SERVICE_URL!' | Set-Content kubernetes\deployment.yaml"

echo Manifests updated successfully.

echo.
echo ==========================================
echo Applying Kubernetes manifests...
echo ==========================================

echo Creating namespace...
kubectl apply -f kubernetes\namespace.yaml
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to create namespace!
    exit /b 1
)

echo.
echo Deploying application...
kubectl apply -f kubernetes\deployment.yaml
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to deploy application!
    exit /b 1
)

echo.
echo Creating service...
kubectl apply -f kubernetes\service.yaml
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to create service!
    exit /b 1
)

echo.
echo Creating ingress...
kubectl apply -f kubernetes\ingress.yaml
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to create ingress!
    exit /b 1
)

echo.
echo ==========================================
echo Waiting for deployment rollout...
echo ==========================================
kubectl rollout status deployment/resortslite -n resortslite --timeout=5m
if !ERRORLEVEL! neq 0 (
    echo ERROR: Deployment rollout failed!
    echo Check pod status with: kubectl get pods -n resortslite
    echo Check logs with: kubectl logs -n resortslite -l app=resortslite
    exit /b 1
)

echo.
echo ==========================================
echo Deployment Status
echo ==========================================
kubectl get pods,svc,ingress -n resortslite

echo.
echo ==========================================
echo SUCCESS!
echo ==========================================
echo ResortsLite has been deployed to GKE successfully!
echo.
echo Useful commands:
echo   View pods:        kubectl get pods -n resortslite
echo   View logs:        kubectl logs -n resortslite -l app=resortslite
echo   View service:     kubectl get svc -n resortslite
echo   View ingress:     kubectl get ingress -n resortslite
echo   Scale deployment: kubectl scale deployment/resortslite -n resortslite --replicas=3
echo.
echo To access the application:
echo   1. Get the ingress IP: kubectl get ingress resortslite-ingress -n resortslite
echo   2. Access via: http://^<INGRESS_IP^>/
echo ==========================================

endlocal
