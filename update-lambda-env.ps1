# Update Lambda Environment Variables - Switch between Staging/Prod

param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("staging", "prod")]
    [string]$Environment
)

$region = "eu-west-1"

# Define Lambda functions
$functions = @(
    "GuideYaAuthHandler",
    "GuideYaInitiateUpload",
    "GuideYaUploadHandler",
    "GuideYaProcessUpload",
    "GuideYaGetUploadStatus",
    "GuideYaContactRequestHandler"
)

# Set table names based on environment
if ($Environment -eq "staging") {
    $uploadsTable = "GuideYa-Uploads-staging"
    $transactionsTable = "GuideYa-Transactions-staging"
    $s3Prefix = "staging/"
    Write-Host "🔵 STAGING MODE" -ForegroundColor Cyan
} else {
    $uploadsTable = "GuideYa-Uploads"
    $transactionsTable = "GuideYa-Transactions"
    $s3Prefix = "prod/"
    Write-Host "🔴 PRODUCTION MODE" -ForegroundColor Red
}

Write-Host "Updating Lambda environment variables for: $Environment" -ForegroundColor Green
Write-Host "  UPLOADS_TABLE=$uploadsTable"
Write-Host "  TRANSACTIONS_TABLE=$transactionsTable"
Write-Host "  S3_PREFIX=$s3Prefix"
Write-Host ""

foreach ($funcName in $functions) {
    # Add suffix for staging
    if ($Environment -eq "staging") {
        $fullFuncName = "$funcName-staging"
    } else {
        $fullFuncName = $funcName
    }

    Write-Host "Updating: $fullFuncName" -ForegroundColor Yellow

    # Update environment variables
    aws lambda update-function-configuration `
        --function-name $fullFuncName `
        --environment "Variables={UPLOADS_TABLE=$uploadsTable,TRANSACTIONS_TABLE=$transactionsTable,S3_PREFIX=$s3Prefix}" `
        --region $region `
        2>&1 | Out-Null

    if ($?) {
        Write-Host "  ✅ Updated" -ForegroundColor Green
    } else {
        Write-Host "  ❌ Failed" -ForegroundColor Red
    }
}

Write-Host "`n✅ All Lambda environment variables updated to $Environment" -ForegroundColor Green
