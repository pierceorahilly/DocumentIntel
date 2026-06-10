# Create API Gateway staging stage and update Lambda integrations
# Usage: .\create-staging-api-stage.ps1

$APIId = "vz1307i8t2"
$Region = "eu-west-1"
$StageName = "staging"
$ProdStageName = "prod"

Write-Host "Starting API Gateway staging stage setup..." -ForegroundColor Green
Write-Host "API ID: $APIId"
Write-Host "Region: $Region"
Write-Host ""

# Step 1: Create the staging stage
Write-Host "Step 1: Creating staging stage..." -ForegroundColor Cyan
try {
    aws apigateway create-stage `
        --rest-api-id $APIId `
        --stage-name $StageName `
        --deployment-id (aws apigateway get-stage --rest-api-id $APIId --stage-name $ProdStageName --query 'deploymentId' --output text) `
        --region $Region
    Write-Host "✅ Staging stage created" -ForegroundColor Green
} catch {
    Write-Host "⚠️ Stage might already exist, continuing..." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Step 2: Getting all resources..." -ForegroundColor Cyan

# Get all resources
$resources = aws apigateway get-resources `
    --rest-api-id $APIId `
    --region $Region `
    --output json | ConvertFrom-Json

Write-Host "Found $($resources.items.Count) resources" -ForegroundColor Green

# Step 3: Update Lambda integrations for each resource
Write-Host ""
Write-Host "Step 3: Updating Lambda integrations..." -ForegroundColor Cyan

$updatedCount = 0

foreach ($resource in $resources.items) {
    $resourceId = $resource.id
    $path = $resource.path

    # Get methods for this resource
    try {
        $methods = aws apigateway get-resource `
            --rest-api-id $APIId `
            --resource-id $resourceId `
            --region $Region `
            --output json | ConvertFrom-Json

        $resourceMethods = $methods.resourceMethods
        if ($null -eq $resourceMethods) {
            continue
        }

        foreach ($methodName in $resourceMethods.PSObject.Properties.Name) {
            if ($methodName -eq "OPTIONS") {
                continue
            }

            Write-Host "  Checking $methodName $path..." -ForegroundColor Gray

            try {
                $integration = aws apigateway get-integration `
                    --rest-api-id $APIId `
                    --resource-id $resourceId `
                    --http-method $methodName `
                    --region $Region `
                    --output json | ConvertFrom-Json

                if ($integration.type -eq "AWS_PROXY" -or $integration.type -eq "AWS") {
                    $currentUri = $integration.uri

                    # Extract Lambda function name and create staging version
                    if ($currentUri -match "function:([^/]+)/") {
                        $functionName = $matches[1]
                        $stagingFunctionName = "$functionName-staging"
                        $stagingUri = $currentUri -replace "function:$functionName/", "function:$stagingFunctionName/"

                        Write-Host "    Updating: $functionName -> $stagingFunctionName" -ForegroundColor Yellow

                        # Update the integration to point to staging Lambda
                        aws apigateway update-integration `
                            --rest-api-id $APIId `
                            --resource-id $resourceId `
                            --http-method $methodName `
                            --type $integration.type `
                            --integration-http-method POST `
                            --uri $stagingUri `
                            --region $Region | Out-Null

                        $updatedCount++
                        Write-Host "    ✅ Updated" -ForegroundColor Green
                    }
                }
            } catch {
                # Skip if method has no integration
            }
        }
    } catch {
        # Skip resources without methods
    }
}

Write-Host ""
Write-Host "Step 4: Creating deployment..." -ForegroundColor Cyan

# Create a deployment for the staging stage
$deployment = aws apigateway create-deployment `
    --rest-api-id $APIId `
    --region $Region `
    --output json | ConvertFrom-Json

Write-Host "✅ Deployment created: $($deployment.id)" -ForegroundColor Green

Write-Host ""
Write-Host "Step 5: Updating staging stage..." -ForegroundColor Cyan

aws apigateway update-stage `
    --rest-api-id $APIId `
    --stage-name $StageName `
    --patch-operations op=replace,path=/deploymentId,value=$($deployment.id) `
    --region $Region | Out-Null

Write-Host "✅ Staging stage updated" -ForegroundColor Green

Write-Host ""
Write-Host "================================" -ForegroundColor Green
Write-Host "✅ Setup Complete!" -ForegroundColor Green
Write-Host "================================" -ForegroundColor Green
Write-Host ""
Write-Host "Your staging API Gateway URL:" -ForegroundColor Cyan
Write-Host "https://$APIId.execute-api.$Region.amazonaws.com/$StageName" -ForegroundColor White
Write-Host ""
Write-Host "Updated $updatedCount Lambda integrations to staging versions" -ForegroundColor Green
