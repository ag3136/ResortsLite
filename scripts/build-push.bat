@echo off
setlocal enabledelayedexpansion

:: =============================================================================
:: build-push.bat — Build and push the ResortsLite Docker image (Windows)
:: Usage: scripts\build-push.bat
:: Run from the repository root directory
:: =============================================================================

set PROJECT_NAME=resortslite
set DOCKERFILE_PATH=Dockerfile

echo ==============================================
echo   ResortsLite — Docker Build ^& Push
echo ==============================================
echo.

:: ---------------------------------------------------------------------------
:: Prompt for image tag
:: ---------------------------------------------------------------------------
set /p IMAGE_TAG_INPUT="Enter image tag [latest]: "
if "!IMAGE_TAG_INPUT!"=="" (
    set IMAGE_TAG=latest
) else (
    set IMAGE_TAG=!IMAGE_TAG_INPUT!
)
echo Using tag: !IMAGE_TAG!
echo.

:: ---------------------------------------------------------------------------
:: Registry selection
:: ---------------------------------------------------------------------------
echo Select container registry:
echo   1. AWS ECR
echo   2. Docker Hub
set /p REGISTRY_CHOICE="Enter choice [1]: "
if "!REGISTRY_CHOICE!"=="" set REGISTRY_CHOICE=1

if "!REGISTRY_CHOICE!"=="1" goto :ecr_setup
if "!REGISTRY_CHOICE!"=="2" goto :dockerhub_setup
echo Invalid choice. Exiting.
exit /b 1

:: ---------------------------------------------------------------------------
:ecr_setup
:: ---------------------------------------------------------------------------
set /p AWS_REGION="Enter AWS region [us-east-1]: "
if "!AWS_REGION!"=="" set AWS_REGION=us-east-1

set /p ACCOUNT_ID_INPUT="Enter AWS Account ID (leave blank to auto-detect): "
if "!ACCOUNT_ID_INPUT!"=="" (
    echo Auto-detecting AWS Account ID...
    for /f "tokens=*" %%i in ('aws sts get-caller-identity --query Account --output text') do set ACCOUNT_ID=%%i
    echo Detected Account ID: !ACCOUNT_ID!
) else (
    set ACCOUNT_ID=!ACCOUNT_ID_INPUT!
)

set /p ECR_REPO_INPUT="Enter ECR repository name [!PROJECT_NAME!]: "
if "!ECR_REPO_INPUT!"=="" (
    set ECR_REPO=!PROJECT_NAME!
) else (
    set ECR_REPO=!ECR_REPO_INPUT!
)

set REGISTRY_URL=!ACCOUNT_ID!.dkr.ecr.!AWS_REGION!.amazonaws.com
set FULL_IMAGE_NAME=!REGISTRY_URL!/!ECR_REPO!:!IMAGE_TAG!

echo.
echo Logging in to ECR...
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
    if !ERRORLEVEL! neq 0 (
        echo Failed to create ECR repository.
        exit /b 1
    )
)
echo ECR repository ready: !ECR_REPO!
goto :build_image

:: ---------------------------------------------------------------------------
:dockerhub_setup
:: ---------------------------------------------------------------------------
set /p DOCKER_USERNAME="Enter Docker Hub username: "
set /p DOCKER_PASSWORD="Enter Docker Hub password/token: "
set /p DOCKER_REPO_INPUT="Enter Docker Hub repository [!DOCKER_USERNAME!/!PROJECT_NAME!]: "
if "!DOCKER_REPO_INPUT!"=="" (
    set DOCKER_REPO=!DOCKER_USERNAME!/!PROJECT_NAME!
) else (
    set DOCKER_REPO=!DOCKER_REPO_INPUT!
)

set FULL_IMAGE_NAME=!DOCKER_REPO!:!IMAGE_TAG!

echo.
echo Logging in to Docker Hub...
echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
if !ERRORLEVEL! neq 0 (
    echo Docker Hub login failed.
    exit /b 1
)
goto :build_image

:: ---------------------------------------------------------------------------
:build_image
:: ---------------------------------------------------------------------------
echo.
echo Building Docker image: !FULL_IMAGE_NAME!
echo Build context: . (repository root)
docker build -f !DOCKERFILE_PATH! -t !FULL_IMAGE_NAME! .
if !ERRORLEVEL! neq 0 (
    echo Docker build failed.
    exit /b 1
)
echo Build successful.

:: ---------------------------------------------------------------------------
:push_image
:: ---------------------------------------------------------------------------
echo.
echo Pushing image: !FULL_IMAGE_NAME!
docker push !FULL_IMAGE_NAME!
if !ERRORLEVEL! neq 0 (
    echo Docker push failed.
    exit /b 1
)

echo.
echo ==============================================
echo   Image pushed successfully!
echo   !FULL_IMAGE_NAME!
echo ==============================================

endlocal
