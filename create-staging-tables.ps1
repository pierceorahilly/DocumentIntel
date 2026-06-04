# Simple DynamoDB Staging Table Creation

$region = "eu-west-1"

Write-Host "Creating DynamoDB staging tables..." -ForegroundColor Green

# Table 1: GuideYa-Uploads-staging
Write-Host "`nCreating GuideYa-Uploads-staging..." -ForegroundColor Yellow
aws dynamodb create-table `
    --table-name GuideYa-Uploads-staging `
    --attribute-definitions AttributeName=uploadId,AttributeType=S `
    --key-schema AttributeName=uploadId,KeyType=HASH `
    --billing-mode PAY_PER_REQUEST `
    --region $region 2>&1 | Out-Null

if ($?) {
    Write-Host "✅ GuideYa-Uploads-staging created" -ForegroundColor Green
} else {
    Write-Host "❌ Failed (table may already exist)" -ForegroundColor Red
}

# Table 2: GuideYa-Transactions-staging
Write-Host "`nCreating GuideYa-Transactions-staging..." -ForegroundColor Yellow
aws dynamodb create-table `
    --table-name GuideYa-Transactions-staging `
    --attribute-definitions AttributeName=id,AttributeType=S `
    --key-schema AttributeName=id,KeyType=HASH `
    --billing-mode PAY_PER_REQUEST `
    --region $region 2>&1 | Out-Null

if ($?) {
    Write-Host "✅ GuideYa-Transactions-staging created" -ForegroundColor Green
} else {
    Write-Host "❌ Failed (table may already exist)" -ForegroundColor Red
}

Write-Host "`n✅ Done! Check AWS console to verify tables are ACTIVE" -ForegroundColor Green
