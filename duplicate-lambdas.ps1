# AWS Lambda Duplication Script - Creates staging versions of all 6 functions

$region = "eu-west-1"
$functions = @(
    "GuideYaAuthHandler",
    "GuideYaInitiateUpload",
    "GuideYaUploadHandler",
    "GuideYaProcessUpload",
    "GuideYaGetUploadStatus",
    "GuideYaContactRequestHandler"
)

Write-Host "Starting Lambda duplication to staging..." -ForegroundColor Green

foreach ($funcName in $functions) {
    $stagingName = "$funcName-staging"

    Write-Host "`nProcessing: $funcName → $stagingName" -ForegroundColor Cyan

    # Get the prod function config
    $prodFunc = aws lambda get-function `
        --function-name $funcName `
        --region $region `
        --query 'Configuration' `
        --output json | ConvertFrom-Json

    if ($null -eq $prodFunc) {
        Write-Host "❌ Failed to get $funcName" -ForegroundColor Red
        continue
    }

    # Extract needed values
    $handler = $prodFunc.Handler
    $runtime = $prodFunc.Runtime
    $roleArn = $prodFunc.Role
    $timeout = $prodFunc.Timeout
    $memorySize = $prodFunc.MemorySize
    $description = $prodFunc.Description

    # Get the current code (JAR file location)
    $codeLocation = aws lambda get-function `
        --function-name $funcName `
        --region $region `
        --query 'Code.Location' `
        --output text

    Write-Host "  Role: $roleArn"
    Write-Host "  Handler: $handler"
    Write-Host "  Memory: $memorySize MB"
    Write-Host "  Timeout: $timeout s"

    # Create staging function
    Write-Host "  Creating $stagingName..." -ForegroundColor Yellow

    aws lambda create-function `
        --function-name $stagingName `
        --runtime $runtime `
        --role $roleArn `
        --handler $handler `
        --zip-file fileb://C:\Users\Pierc\Desktop\Github\DocumentIntel\lambda-auth\target\bankbuddy-lambda-auth-1.0.0.jar `
        --timeout $timeout `
        --memory-size $memorySize `
        --description "$description (Staging)" `
        --region $region `
        2>&1 | Out-Null

    if ($?) {
        Write-Host "  ✅ Created $stagingName" -ForegroundColor Green

        # Copy environment variables from prod
        Write-Host "  Copying environment variables..." -ForegroundColor Yellow
        $envVars = $prodFunc.Environment.Variables

        if ($null -ne $envVars) {
            $envJson = $envVars | ConvertTo-Json -Compress
            aws lambda update-function-configuration `
                --function-name $stagingName `
                --environment Variables=$envJson `
                --region $region `
                2>&1 | Out-Null

            Write-Host "  ✅ Environment variables copied" -ForegroundColor Green
        }
    }
    else {
        Write-Host "  ❌ Failed to create $stagingName" -ForegroundColor Red
    }
}

Write-Host "`n✅ Staging Lambda functions created!" -ForegroundColor Green
Write-Host "Next: Create staging DynamoDB tables and update environment variables" -ForegroundColor Cyan
