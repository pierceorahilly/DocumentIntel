# DynamoDB Table Duplication Script - Creates staging tables from prod tables

$region = "eu-west-1"
$tablesToDuplicate = @(
    @{ prod = "GuideYa-Uploads"; staging = "GuideYa-Uploads-staging" },
    @{ prod = "GuideYa-Transactions"; staging = "GuideYa-Transactions-staging" }
)

Write-Host "Starting DynamoDB table duplication..." -ForegroundColor Green

foreach ($tableMapping in $tablesToDuplicate) {
    $prodTableName = $tableMapping.prod
    $stagingTableName = $tableMapping.staging

    Write-Host "`n========================================" -ForegroundColor Cyan
    Write-Host "Processing: $prodTableName → $stagingTableName" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan

    # Get prod table description
    Write-Host "Getting prod table schema: $prodTableName" -ForegroundColor Yellow

    $prodTable = aws dynamodb describe-table `
        --table-name $prodTableName `
        --region $region `
        --query 'Table' `
        --output json | ConvertFrom-Json

    if ($null -eq $prodTable) {
        Write-Host "❌ Failed to get $prodTableName" -ForegroundColor Red
        continue
    }

    Write-Host "✅ Found prod table: $prodTableName" -ForegroundColor Green
    Write-Host "  AttributeDefinitions: $($prodTable.AttributeDefinitions.Count) attributes"
    Write-Host "  KeySchema: $($prodTable.KeySchema.Count) keys"
    Write-Host "  BillingMode: $($prodTable.BillingModeSummary.BillingMode)"

    # Extract table configuration
    $attributeDefinitions = $prodTable.AttributeDefinitions | ConvertTo-Json -Compress
    $keySchema = $prodTable.KeySchema | ConvertTo-Json -Compress
    $billingMode = $prodTable.BillingModeSummary.BillingMode

    # Create the table
    Write-Host "Creating staging table: $stagingTableName" -ForegroundColor Yellow

    aws dynamodb create-table `
        --table-name $stagingTableName `
        --attribute-definitions $attributeDefinitions `
        --key-schema $keySchema `
        --billing-mode $billingMode `
        --region $region `
        2>&1 | Out-Null

    if ($?) {
        Write-Host "✅ Created staging table: $stagingTableName" -ForegroundColor Green
        Write-Host "⏳ Waiting for table to be ACTIVE..." -ForegroundColor Yellow

        # Wait for table to be active
        $maxWaitTime = 300  # 5 minutes
        $elapsed = 0
        $checkInterval = 5

        while ($elapsed -lt $maxWaitTime) {
            $tableStatus = aws dynamodb describe-table `
                --table-name $stagingTableName `
                --region $region `
                --query 'Table.TableStatus' `
                --output text

            if ($tableStatus -eq "ACTIVE") {
                Write-Host "✅ Table is ACTIVE and ready to use!" -ForegroundColor Green
                break
            }

            Write-Host "  Current status: $tableStatus (waiting...)" -ForegroundColor Yellow
            Start-Sleep -Seconds $checkInterval
            $elapsed += $checkInterval
        }

        if ($tableStatus -ne "ACTIVE") {
            Write-Host "⚠️ Table creation timed out. Check AWS console to verify." -ForegroundColor Yellow
        }
    }
    else {
        Write-Host "❌ Failed to create staging table: $stagingTableName" -ForegroundColor Red
    }
}

Write-Host "`n✅ DynamoDB staging setup complete!" -ForegroundColor Green
Write-Host "Next: Update staging Lambda environment variables" -ForegroundColor Cyan
