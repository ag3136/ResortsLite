@echo off
setlocal enabledelayedexpansion

set APP_NAME=resortslite
set NAMESPACE=resortslite
set K8S_DIR=kubernetes

echo ============================================
echo   ResortsLite - Deploy to AWS EKS
echo ============================================
echo.

REM ── AWS / EKS configuration ──────────────────────────────────────────────────
set /p AWS_REGION="Enter AWS region (e.g. us-east-1): "
if "!AWS_REGION!"=="" (
    echo ERROR: AWS region is required.
    exit /b 1
)

set /p CLUSTER_NAME="Enter EKS cluster name: "
if "!CLUSTER_NAME!"=="" (
    echo ERROR: EKS cluster name is required.
    exit /b 1
)

set /p IMAGE_URI="Enter full Docker image URI (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/resortslite:latest): "
if "!IMAGE_URI!"=="" (
    echo ERROR: Image URI is required.
    exit /b 1
)

REM ── Application environment variables ────────────────────────────────────────
echo.
echo --- Application Configuration (press Enter to use defaults) ---

set /p REDIS_HOST="Enter REDIS_HOST (default: localhost): "
if "!REDIS_HOST!"=="" set REDIS_HOST=localhost

set /p REDIS_PORT="Enter REDIS_PORT (default: 6379): "
if "!REDIS_PORT!"=="" set REDIS_PORT=6379

set /p PAYMENT_API_URL="Enter PAYMENT_API_URL (default: http://payment-service.default.svc.cluster.local:9090/payments/charge): "
if "!PAYMENT_API_URL!"=="" set PAYMENT_API_URL=http://payment-service.default.svc.cluster.local:9090/payments/charge

set /p REPORT_BASE_PATH="Enter REPORT_BASE_PATH (default: /var/reports): "
if "!REPORT_BASE_PATH!"=="" set REPORT_BASE_PATH=/var/reports

set /p BACKUP_PATH="Enter BACKUP_PATH (default: /var/backups/resorts): "
if "!BACKUP_PATH!"=="" set BACKUP_PATH=/var/backups/resorts

set /p CACHE_PROVIDER="Enter CACHE_PROVIDER (default: redis): "
if "!CACHE_PROVIDER!"=="" set CACHE_PROVIDER=redis

REM ── Configure kubectl ─────────────────────────────────────────────────────────
echo.
echo Configuring kubectl for EKS cluster: !CLUSTER_NAME! in !AWS_REGION! ...
aws eks update-kubeconfig --region !AWS_REGION! --name !CLUSTER_NAME!
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to configure kubectl.
    exit /b 1
)

echo Verifying cluster connectivity...
kubectl cluster-info
if !ERRORLEVEL! neq 0 (
    echo ERROR: Cannot connect to EKS cluster.
    exit /b 1
)

REM ── Substitute placeholders in manifests ─────────────────────────────────────
echo.
echo Updating Kubernetes manifests with deployment values...

powershell -Command "(Get-Content '!K8S_DIR!\deployment.yaml') -replace '\{\{IMAGE_URI\}\}', '!IMAGE_URI!' | Set-Content '!K8S_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!K8S_DIR!\deployment.yaml') -replace '\{\{REDIS_HOST\}\}', '!REDIS_HOST!' | Set-Content '!K8S_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!K8S_DIR!\deployment.yaml') -replace '\{\{REDIS_PORT\}\}', '!REDIS_PORT!' | Set-Content '!K8S_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!K8S_DIR!\deployment.yaml') -replace '\{\{PAYMENT_API_URL\}\}', '!PAYMENT_API_URL!' | Set-Content '!K8S_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!K8S_DIR!\deployment.yaml') -replace '\{\{REPORT_BASE_PATH\}\}', '!REPORT_BASE_PATH!' | Set-Content '!K8S_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!K8S_DIR!\deployment.yaml') -replace '\{\{BACKUP_PATH\}\}', '!BACKUP_PATH!' | Set-Content '!K8S_DIR!\deployment.yaml'"
powershell -Command "(Get-Content '!K8S_DIR!\deployment.yaml') -replace '\{\{CACHE_PROVIDER\}\}', '!CACHE_PROVIDER!' | Set-Content '!K8S_DIR!\deployment.yaml'"

REM ── Apply manifests ───────────────────────────────────────────────────────────
echo.
echo Applying Kubernetes manifests...

echo   [1/4] Applying namespace...
kubectl apply -f !K8S_DIR!\namespace.yaml
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply namespace. & exit /b 1 )

echo   [2/4] Applying deployment...
kubectl apply -f !K8S_DIR!\deployment.yaml
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply deployment. & exit /b 1 )

echo   [3/4] Applying service...
kubectl apply -f !K8S_DIR!\service.yaml
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply service. & exit /b 1 )

echo   [4/4] Applying ingress...
kubectl apply -f !K8S_DIR!\ingress.yaml
if !ERRORLEVEL! neq 0 ( echo ERROR: Failed to apply ingress. & exit /b 1 )

REM ── Wait for rollout ──────────────────────────────────────────────────────────
echo.
echo Waiting for deployment rollout to complete...
kubectl rollout status deployment/!APP_NAME! -n !NAMESPACE! --timeout=300s
if !ERRORLEVEL! neq 0 (
    echo ERROR: Deployment rollout failed.
    echo Run: kubectl rollout undo deployment/!APP_NAME! -n !NAMESPACE!
    exit /b 1
)

REM ── Verify resources ──────────────────────────────────────────────────────────
echo.
echo Verifying deployed resources...
kubectl get pods,svc,ingress -n !NAMESPACE!

echo.
echo ============================================
echo   Deployment complete!
echo   Check ingress for the application URL.
echo   Health check: /actuator/health
echo ============================================
echo.
echo Rollback command (if needed):
echo   kubectl rollout undo deployment/!APP_NAME! -n !NAMESPACE!

endlocal
