@echo off
setlocal enabledelayedexpansion

REM Build and Push Docker Image Script for ResortsLite (Windows)
REM Supports AWS ECR and Docker Hub registries

echo ==========================================
echo ResortsLite - Docker Build and Push Script
echo ==========================================
echo.

REM Project configuration
set PROJECT_NAME=ResortsLite
set DOCKERFILE_PATH=Dockerfile

REM Sanitize project name for Docker tag (lowercase, hyphenate)
set IMAGE_NAME=resortslite

echo Project: %PROJECT_NAME%
echo Sanitized Image Name: %IMAGE_NAME%
echo.

REM Prompt for registry type
echo Select Docker Registry:
echo 1. AWS ECR (Elastic Container Registry)
echo 2. Docker Hub
set /p REGISTRY_CHOICE="Enter choice (1 or 2): "

if "!REGISTRY_CHOICE!"=="1" (
    echo.
    echo === AWS ECR Configuration ===
    
    REM Prompt for AWS region
    set /p AWS_REGION="Enter AWS Region (e.g., us-east-1): "
    if "!AWS_REGION!"=="" (
        echo Error: AWS Region is required
        exit /b 1
    )
    
    REM Prompt for AWS Account ID
    set /p AWS_ACCOUNT_ID="Enter AWS Account ID: "
    if "!AWS_ACCOUNT_ID!"=="" (
        echo Error: AWS Account ID is required
        exit /b 1
    )
    
    REM Prompt for ECR repository name
    set /p ECR_REPO="Enter ECR Repository Name (default: %IMAGE_NAME%): "
    if "!ECR_REPO!"=="" set ECR_REPO=%IMAGE_NAME%
    
    REM Construct ECR registry URL
    set REGISTRY_URL=!AWS_ACCOUNT_ID!.dkr.ecr.!AWS_REGION!.amazonaws.com
    
    echo.
    echo Authenticating with AWS ECR...
    for /f "delims=" %%i in ('aws ecr get-login-password --region !AWS_REGION!') do set ECR_PASSWORD=%%i
    echo !ECR_PASSWORD! | docker login --username AWS --password-stdin !REGISTRY_URL!
    
    if !ERRORLEVEL! neq 0 (
        echo Error: ECR authentication failed
        exit /b 1
    )
    
    echo ECR authentication successful
    
    REM Check if ECR repository exists, create if not
    echo Checking ECR repository...
    aws ecr describe-repositories --repository-names !ECR_REPO! --region !AWS_REGION! >nul 2>&1
    if !ERRORLEVEL! neq 0 (
        echo Repository does not exist. Creating ECR repository: !ECR_REPO!
        aws ecr create-repository --repository-name !ECR_REPO! --region !AWS_REGION!
        if !ERRORLEVEL! neq 0 (
            echo Error: Failed to create ECR repository
            exit /b 1
        )
        echo ECR repository created successfully
    )
    
    REM Prompt for image tag
    set /p IMAGE_TAG="Enter image tag (default: latest): "
    if "!IMAGE_TAG!"=="" set IMAGE_TAG=latest
    
    set FULL_IMAGE_NAME=!REGISTRY_URL!/!ECR_REPO!:!IMAGE_TAG!
    
) else if "!REGISTRY_CHOICE!"=="2" (
    echo.
    echo === Docker Hub Configuration ===
    
    REM Prompt for Docker Hub username
    set /p DOCKER_USERNAME="Enter Docker Hub Username: "
    if "!DOCKER_USERNAME!"=="" (
        echo Error: Docker Hub username is required
        exit /b 1
    )
    
    REM Prompt for Docker Hub password
    set /p DOCKER_PASSWORD="Enter Docker Hub Password: "
    if "!DOCKER_PASSWORD!"=="" (
        echo Error: Docker Hub password is required
        exit /b 1
    )
    
    echo.
    echo Authenticating with Docker Hub...
    echo !DOCKER_PASSWORD! | docker login --username !DOCKER_USERNAME! --password-stdin
    
    if !ERRORLEVEL! neq 0 (
        echo Error: Docker Hub authentication failed
        exit /b 1
    )
    
    echo Docker Hub authentication successful
    
    REM Prompt for image tag
    set /p IMAGE_TAG="Enter image tag (default: latest): "
    if "!IMAGE_TAG!"=="" set IMAGE_TAG=latest
    
    set FULL_IMAGE_NAME=!DOCKER_USERNAME!/%IMAGE_NAME%:!IMAGE_TAG!
    
) else (
    echo Error: Invalid choice. Please select 1 or 2
    exit /b 1
)

echo.
echo ==========================================
echo Building Docker Image
echo ==========================================
echo Image: !FULL_IMAGE_NAME!
echo.

REM Build Docker image
docker build -f %DOCKERFILE_PATH% -t !FULL_IMAGE_NAME! .

if !ERRORLEVEL! neq 0 (
    echo Error: Docker build failed
    exit /b 1
)

echo.
echo Docker build completed successfully

echo.
echo ==========================================
echo Pushing Docker Image
echo ==========================================
echo Pushing: !FULL_IMAGE_NAME!
echo.

REM Push Docker image
docker push !FULL_IMAGE_NAME!

if !ERRORLEVEL! neq 0 (
    echo Error: Docker push failed
    exit /b 1
)

echo.
echo ==========================================
echo Build and Push Completed Successfully
echo ==========================================
echo Image: !FULL_IMAGE_NAME!
echo.
echo Next steps:
echo 1. Update kubernetes/deployment.yaml with image URI: !FULL_IMAGE_NAME!
echo 2. Run deploy-image.bat to deploy to AWS EKS
echo.

endlocal
