@echo off
setlocal enabledelayedexpansion

set PROJECT_NAME=resortslite

echo ============================================
echo   ResortsLite - Docker Build ^& Push Script
echo ============================================
echo.

REM ── Registry selection ──────────────────────────────────────────────────────
echo Select container registry:
echo   1) AWS ECR
echo   2) Docker Hub
set /p REGISTRY_CHOICE="Enter choice [1 or 2]: "

REM ── Image tag ────────────────────────────────────────────────────────────────
set /p RAW_TAG="Enter image tag (press Enter for 'latest'): "
if "!RAW_TAG!"=="" (
    set IMAGE_TAG=latest
) else (
    set IMAGE_TAG=!RAW_TAG!
)

REM ── Registry-specific setup ──────────────────────────────────────────────────
if "!REGISTRY_CHOICE!"=="1" (
    REM ── AWS ECR ──
    set /p AWS_REGION="Enter AWS region (e.g. us-east-1): "
    set /p AWS_ACCOUNT_ID="Enter AWS account ID: "
    set ECR_REPO=!PROJECT_NAME!
    set REGISTRY_URL=!AWS_ACCOUNT_ID!.dkr.ecr.!AWS_REGION!.amazonaws.com
    set FULL_IMAGE_NAME=!REGISTRY_URL!/!ECR_REPO!:!IMAGE_TAG!

    echo.
    echo Logging in to AWS ECR...
    aws ecr get-login-password --region !AWS_REGION! | docker login --username AWS --password-stdin !REGISTRY_URL!
    if !ERRORLEVEL! neq 0 (
        echo ECR login failed.
        exit /b 1
    )

    echo Ensuring ECR repository exists...
    aws ecr describe-repositories --repository-names !ECR_REPO! --region !AWS_REGION! >nul 2>&1
    if !ERRORLEVEL! neq 0 (
        echo Creating ECR repository...
        aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!
    )

) else if "!REGISTRY_CHOICE!"=="2" (
    REM ── Docker Hub ──
    set /p DOCKER_USERNAME="Enter Docker Hub username: "
    set /p DOCKER_PASSWORD="Enter Docker Hub password/token: "
    set FULL_IMAGE_NAME=!DOCKER_USERNAME!/!PROJECT_NAME!:!IMAGE_TAG!

    echo.
    echo Logging in to Docker Hub...
    echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
    if !ERRORLEVEL! neq 0 (
        echo Docker Hub login failed.
        exit /b 1
    )

) else (
    echo Invalid choice. Exiting.
    exit /b 1
)

REM ── Build ────────────────────────────────────────────────────────────────────
echo.
echo Building Docker image: !FULL_IMAGE_NAME!
docker build -f Dockerfile -t !FULL_IMAGE_NAME! .
if !ERRORLEVEL! neq 0 (
    echo Docker build failed.
    exit /b 1
)

echo.
echo Pushing image: !FULL_IMAGE_NAME!
docker push !FULL_IMAGE_NAME!
if !ERRORLEVEL! neq 0 (
    echo Docker push failed.
    exit /b 1
)

echo.
echo ============================================
echo   Build ^& push complete!
echo   Image: !FULL_IMAGE_NAME!
echo ============================================

endlocal
