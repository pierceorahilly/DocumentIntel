# Guide-ya Complete AWS Deployment Script
# Deploys everything end-to-end automatically

param(
    [switch]$SkipBuild,
    [switch]$DryRun,
    [switch]$CleanupOnly
)

$ErrorActionPreference = "Stop"

# Output functions
function Write-Step {
    Write-Host "`n=========================================" -ForegroundColor Cyan
    Write-Host " $args" -ForegroundColor Cyan
    Write-Host "=========================================" -ForegroundColor Cyan
}
function Write-Success { Write-Host "[OK] $args" -ForegroundColor Green }
function Write-Info { Write-Host "  $args" -ForegroundColor Gray }
function Write-Warning { Write-Host "[WARN] $args" -ForegroundColor Yellow }
function Write-Error { Write-Host "[ERROR] $args" -ForegroundColor Red }
function Write-SubStep { Write-Host "`n--- $args ---" -ForegroundColor Yellow }

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  Guide-ya AWS Deployment Script" -ForegroundColor Green
Write-Host "  Complete End-to-End Setup" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green

# Load configuration
Write-Step "Loading Configuration"

$configFile = Join-Path $PSScriptRoot "config.properties"
if (-not (Test-Path $configFile)) {
    Write-Error "Configuration file not found: $configFile"
    Write-Info "Please copy config.properties.template and fill in your values"
    exit 1
}

$config = @{}
Get-Content $configFile | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]+?)\s*=\s*(.+?)\s*$') {
        $config[$matches[1]] = $matches[2]
    }
}

# Validate configuration
$requiredFields = @('AWS_REGION', 'AWS_ACCOUNT_ID', 'S3_UPLOADS_BUCKET', 'S3_LAMBDA_BUCKET')
$missingFields = @()

foreach ($field in $requiredFields) {
    if (-not $config[$field] -or $config[$field] -match 'YOUR_.*_HERE') {
        $missingFields += $field
    }
}

if ($missingFields.Count -gt 0) {
    Write-Error "Please update config.properties with these values:"
    $missingFields | ForEach-Object { Write-Error "  - $_" }
    exit 1
}

Write-Success "Configuration loaded"
Write-Info "Region: $($config['AWS_REGION'])"
Write-Info "Account: $($config['AWS_ACCOUNT_ID'])"

# Check prerequisites
Write-Step "Checking Prerequisites"

try {
    $null = aws --version
    Write-Success "AWS CLI installed"
} catch {
    Write-Error "AWS CLI not found. Install: https://aws.amazon.com/cli/"
    exit 1
}

try {
    $identity = aws sts get-caller-identity --output json | ConvertFrom-Json
    Write-Success "AWS credentials configured"
    Write-Info "Account: $($identity.Account)"
    Write-Info "User: $($identity.Arn.Split('/')[-1])"

    if ($identity.Account -ne $config['AWS_ACCOUNT_ID']) {
        Write-Warning "Account ID mismatch!"
        Write-Warning "  Config: $($config['AWS_ACCOUNT_ID'])"
        Write-Warning "  AWS CLI: $($identity.Account)"
        $response = Read-Host "Continue anyway? (y/n)"
        if ($response -ne 'y') { exit 1 }
    }
} catch {
    Write-Error "AWS credentials not configured. Run: aws configure"
    exit 1
}

try {
    $null = java -version 2>&1
    Write-Success "Java installed"
} catch {
    Write-Error "Java not found. Install Java 17+"
    exit 1
}

try {
    $null = mvn -version 2>&1
    Write-Success "Maven installed"
} catch {
    Write-Error "Maven not found. Install: https://maven.apache.org/"
    exit 1
}

if ($DryRun) {
    Write-Warning "`nDRY RUN MODE - No changes will be made"
}

# Global variables for created resources
$script:userPoolId = $null
$script:clientId = $null
$script:roleArn = $null
$script:authLambdaArn = $null
$script:uploadLambdaArn = $null
$script:apiId = $null
$script:apiUrl = $null

# Step 1: Build Lambda JAR
Write-Step "Step 1/9: Building Lambda JAR"

$projectRoot = Split-Path -Parent $PSScriptRoot
$jarFile = Join-Path $projectRoot "target\lambda-auth-1.0.0.jar"

if ($SkipBuild -and (Test-Path $jarFile)) {
    Write-Warning "Skipping build (using existing JAR)"
} else {
    Write-Info "Running: mvn clean package -DskipTests"
    Push-Location $projectRoot

    if (-not $DryRun) {
        mvn clean package -DskipTests 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Write-Error "Maven build failed"
            exit 1
        }
    }

    Pop-Location
    Write-Success "Lambda JAR built"
}

if (-not $DryRun -and -not (Test-Path $jarFile)) {
    Write-Error "JAR file not found: $jarFile"
    exit 1
}

$jarSize = if (Test-Path $jarFile) { (Get-Item $jarFile).Length / 1MB } else { 0 }
Write-Info "JAR size: $($jarSize.ToString('F2')) MB"

# Step 2: Create S3 Buckets
Write-Step "Step 2/9: Creating S3 Buckets"

function Create-S3BucketIfNeeded {
    param($name, $region)

    $exists = aws s3api head-bucket --bucket $name 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Success "Bucket exists: $name"
        return $true
    }

    Write-Info "Creating bucket: $name"
    if (-not $DryRun) {
        if ($region -eq "us-east-1") {
            aws s3 mb "s3://$name" --region $region 2>&1 | Out-Null
        } else {
            aws s3api create-bucket --bucket $name --region $region `
                --create-bucket-configuration LocationConstraint=$region 2>&1 | Out-Null
        }

        if ($LASTEXITCODE -ne 0) {
            Write-Error "Failed to create bucket: $name"
            Write-Info "Bucket names must be globally unique. Try a different name."
            return $false
        }

        # Block public access
        aws s3api put-public-access-block --bucket $name `
            --public-access-block-configuration "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true" 2>&1 | Out-Null
    }

    Write-Success "Created bucket: $name"
    return $true
}

$success = Create-S3BucketIfNeeded $config['S3_LAMBDA_BUCKET'] $config['AWS_REGION']
if (-not $success) { exit 1 }

$success = Create-S3BucketIfNeeded $config['S3_UPLOADS_BUCKET'] $config['AWS_REGION']
if (-not $success) { exit 1 }

# Upload Lambda JAR
Write-SubStep "Uploading Lambda JAR to S3"
if (-not $DryRun) {
    aws s3 cp $jarFile "s3://$($config['S3_LAMBDA_BUCKET'])/lambda-auth-1.0.0.jar" 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to upload JAR"
        exit 1
    }
}
Write-Success "Uploaded Lambda JAR"

# Step 3: Create Cognito User Pool
Write-Step "Step 3/9: Creating Cognito User Pool"

$userPools = aws cognito-idp list-user-pools --max-results 60 --output json | ConvertFrom-Json
$existingPool = $userPools.UserPools | Where-Object { $_.Name -eq $config['COGNITO_USER_POOL_NAME'] } | Select-Object -First 1

if ($existingPool) {
    $script:userPoolId = $existingPool.Id
    Write-Success "User Pool exists: $script:userPoolId"
} elseif (-not $DryRun) {
    Write-Info "Creating User Pool..."

    $poolJson = @"
{
    "PoolName": "$($config['COGNITO_USER_POOL_NAME'])",
    "Policies": {
        "PasswordPolicy": {
            "MinimumLength": 8,
            "RequireUppercase": true,
            "RequireLowercase": true,
            "RequireNumbers": true,
            "RequireSymbols": true
        }
    },
    "AutoVerifiedAttributes": ["email"],
    "UsernameAttributes": ["email"],
    "Schema": [
        {
            "Name": "email",
            "AttributeDataType": "String",
            "Required": true,
            "Mutable": false
        },
        {
            "Name": "name",
            "AttributeDataType": "String",
            "Required": true,
            "Mutable": true
        },
        {
            "Name": "birthdate",
            "AttributeDataType": "String",
            "Mutable": true
        },
        {
            "Name": "address",
            "AttributeDataType": "String",
            "Mutable": true
        }
    ]
}
"@

    $result = aws cognito-idp create-user-pool --cli-input-json $poolJson --output json | ConvertFrom-Json
    $script:userPoolId = $result.UserPool.Id
    Write-Success "Created User Pool: $script:userPoolId"
}

# Create App Client
if ($script:userPoolId -and -not $DryRun) {
    $clients = aws cognito-idp list-user-pool-clients --user-pool-id $script:userPoolId --output json | ConvertFrom-Json
    $existingClient = $clients.UserPoolClients | Where-Object { $_.ClientName -eq $config['COGNITO_APP_CLIENT_NAME'] } | Select-Object -First 1

    if ($existingClient) {
        $script:clientId = $existingClient.ClientId
        Write-Success "App Client exists: $script:clientId"
    } else {
        Write-Info "Creating App Client..."
        $result = aws cognito-idp create-user-pool-client `
            --user-pool-id $script:userPoolId `
            --client-name $config['COGNITO_APP_CLIENT_NAME'] `
            --no-generate-secret `
            --explicit-auth-flows ALLOW_USER_PASSWORD_AUTH ALLOW_REFRESH_TOKEN_AUTH ALLOW_USER_SRP_AUTH `
            --output json | ConvertFrom-Json

        $script:clientId = $result.UserPoolClient.ClientId
        Write-Success "Created App Client: $script:clientId"
    }
}

# Step 4: Create DynamoDB Tables
Write-Step "Step 4/9: Creating DynamoDB Tables"

function Create-DynamoTable {
    param($tableName, $keySchema, $attributeDefs, $gsis = $null)

    $exists = aws dynamodb describe-table --table-name $tableName 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Success "Table exists: $tableName"
        return $true
    }

    Write-Info "Creating table: $tableName"
    if (-not $DryRun) {
        $cmd = "aws dynamodb create-table --table-name $tableName " +
               "--attribute-definitions $attributeDefs " +
               "--key-schema $keySchema " +
               "--billing-mode PAY_PER_REQUEST " +
               "--region $($config['AWS_REGION'])"

        if ($gsis) {
            $cmd += " --global-secondary-indexes '$gsis'"
        }

        Invoke-Expression $cmd 2>&1 | Out-Null

        if ($LASTEXITCODE -ne 0) {
            Write-Error "Failed to create table: $tableName"
            return $false
        }
    }

    Write-Success "Created table: $tableName"
    return $true
}

# Users table
$success = Create-DynamoTable `
    -tableName $config['DYNAMODB_USERS_TABLE'] `
    -attributeDefs 'AttributeName=userId,AttributeType=S' `
    -keySchema 'AttributeName=userId,KeyType=HASH'

if (-not $success) { exit 1 }

# Uploads table with GSI
$uploadsGSI = @"
[{
    \"IndexName\": \"UserUploadsIndex\",
    \"KeySchema\": [
        {\"AttributeName\": \"userId\", \"KeyType\": \"HASH\"},
        {\"AttributeName\": \"uploadDate\", \"KeyType\": \"RANGE\"}
    ],
    \"Projection\": {\"ProjectionType\": \"ALL\"}
}]
"@

$success = Create-DynamoTable `
    -tableName $config['DYNAMODB_UPLOADS_TABLE'] `
    -attributeDefs 'AttributeName=uploadId,AttributeType=S AttributeName=userId,AttributeType=S AttributeName=uploadDate,AttributeType=S' `
    -keySchema 'AttributeName=uploadId,KeyType=HASH' `
    -gsis $uploadsGSI

if (-not $success) { exit 1 }

# Transactions table with GSIs
$transactionsGSI = @"
[{
    \"IndexName\": \"UserTransactionsIndex\",
    \"KeySchema\": [
        {\"AttributeName\": \"userId\", \"KeyType\": \"HASH\"},
        {\"AttributeName\": \"date\", \"KeyType\": \"RANGE\"}
    ],
    \"Projection\": {\"ProjectionType\": \"ALL\"}
},
{
    \"IndexName\": \"UploadTransactionsIndex\",
    \"KeySchema\": [
        {\"AttributeName\": \"uploadId\", \"KeyType\": \"HASH\"}
    ],
    \"Projection\": {\"ProjectionType\": \"ALL\"}
}]
"@

$success = Create-DynamoTable `
    -tableName $config['DYNAMODB_TRANSACTIONS_TABLE'] `
    -attributeDefs 'AttributeName=transactionId,AttributeType=S AttributeName=userId,AttributeType=S AttributeName=uploadId,AttributeType=S AttributeName=date,AttributeType=S' `
    -keySchema 'AttributeName=transactionId,KeyType=HASH' `
    -gsis $transactionsGSI

if (-not $success) { exit 1 }

# Step 5: Create IAM Role
Write-Step "Step 5/9: Creating IAM Execution Role"

$roleName = $config['IAM_ROLE_NAME']
$existingRole = aws iam get-role --role-name $roleName 2>&1

if ($LASTEXITCODE -eq 0) {
    $roleInfo = aws iam get-role --role-name $roleName --output json | ConvertFrom-Json
    $script:roleArn = $roleInfo.Role.Arn
    Write-Success "IAM Role exists: $script:roleArn"
} elseif (-not $DryRun) {
    Write-Info "Creating IAM role..."

    # Trust policy - allows Lambda to assume this role
    $trustPolicy = @"
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Principal": {
                "Service": "lambda.amazonaws.com"
            },
            "Action": "sts:AssumeRole"
        }
    ]
}
"@

    $result = aws iam create-role --role-name $roleName `
        --assume-role-policy-document $trustPolicy `
        --description "Execution role for Guide-ya Lambda functions" `
        --output json | ConvertFrom-Json

    $script:roleArn = $result.Role.Arn
    Write-Success "Created IAM role: $script:roleArn"

    # Inline policy with least-privilege permissions
    Write-SubStep "Attaching permissions policy"

    $policyDocument = @"
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "DynamoDBAccess",
            "Effect": "Allow",
            "Action": [
                "dynamodb:GetItem",
                "dynamodb:PutItem",
                "dynamodb:UpdateItem",
                "dynamodb:Query",
                "dynamodb:Scan",
                "dynamodb:BatchWriteItem"
            ],
            "Resource": [
                "arn:aws:dynamodb:$($config['AWS_REGION']):$($config['AWS_ACCOUNT_ID']):table/$($config['DYNAMODB_USERS_TABLE'])",
                "arn:aws:dynamodb:$($config['AWS_REGION']):$($config['AWS_ACCOUNT_ID']):table/$($config['DYNAMODB_UPLOADS_TABLE'])",
                "arn:aws:dynamodb:$($config['AWS_REGION']):$($config['AWS_ACCOUNT_ID']):table/$($config['DYNAMODB_TRANSACTIONS_TABLE'])",
                "arn:aws:dynamodb:$($config['AWS_REGION']):$($config['AWS_ACCOUNT_ID']):table/$($config['DYNAMODB_UPLOADS_TABLE'])/index/*",
                "arn:aws:dynamodb:$($config['AWS_REGION']):$($config['AWS_ACCOUNT_ID']):table/$($config['DYNAMODB_TRANSACTIONS_TABLE'])/index/*"
            ]
        },
        {
            "Sid": "S3Access",
            "Effect": "Allow",
            "Action": [
                "s3:PutObject",
                "s3:GetObject",
                "s3:ListBucket"
            ],
            "Resource": [
                "arn:aws:s3:::$($config['S3_UPLOADS_BUCKET'])",
                "arn:aws:s3:::$($config['S3_UPLOADS_BUCKET'])/*"
            ]
        },
        {
            "Sid": "TextractAccess",
            "Effect": "Allow",
            "Action": [
                "textract:AnalyzeDocument",
                "textract:StartDocumentAnalysis",
                "textract:GetDocumentAnalysis"
            ],
            "Resource": "*"
        },
        {
            "Sid": "BedrockAccess",
            "Effect": "Allow",
            "Action": [
                "bedrock:InvokeModel"
            ],
            "Resource": "arn:aws:bedrock:$($config['AWS_REGION'])::foundation-model/$($config['BEDROCK_MODEL_ID'])"
        },
        {
            "Sid": "CloudWatchLogsAccess",
            "Effect": "Allow",
            "Action": [
                "logs:CreateLogGroup",
                "logs:CreateLogStream",
                "logs:PutLogEvents"
            ],
            "Resource": "arn:aws:logs:$($config['AWS_REGION']):$($config['AWS_ACCOUNT_ID']):log-group:/aws/lambda/*"
        },
        {
            "Sid": "CognitoAccess",
            "Effect": "Allow",
            "Action": [
                "cognito-idp:SignUp",
                "cognito-idp:ConfirmSignUp",
                "cognito-idp:InitiateAuth",
                "cognito-idp:ForgotPassword",
                "cognito-idp:ConfirmForgotPassword",
                "cognito-idp:AdminGetUser"
            ],
            "Resource": "arn:aws:cognito-idp:$($config['AWS_REGION']):$($config['AWS_ACCOUNT_ID']):userpool/*"
        }
    ]
}
"@

    $policyDocument | Out-File -FilePath "$PSScriptRoot\temp-policy.json" -Encoding UTF8

    aws iam put-role-policy `
        --role-name $roleName `
        --policy-name GuideYaLambdaPolicy `
        --policy-document file://$PSScriptRoot/temp-policy.json 2>&1 | Out-Null

    Remove-Item "$PSScriptRoot\temp-policy.json" -Force

    Write-Success "Attached inline policy"
    Write-Info "Waiting 10 seconds for IAM role to propagate..."
    Start-Sleep -Seconds 10
}

Write-Info "Role ARN: $script:roleArn"

# Step 6: Deploy Lambda Functions
Write-Step "Step 6/9: Deploying Lambda Functions"

# Lambda environment variables
$authEnvVars = @"
Variables={
    COGNITO_USER_POOL_ID=$script:userPoolId,
    COGNITO_CLIENT_ID=$script:clientId,
    DYNAMODB_USERS_TABLE=$($config['DYNAMODB_USERS_TABLE']),
    S3_UPLOADS_BUCKET=$($config['S3_UPLOADS_BUCKET'])
}
"@

$uploadEnvVars = @"
Variables={
    COGNITO_USER_POOL_ID=$script:userPoolId,
    DYNAMODB_USERS_TABLE=$($config['DYNAMODB_USERS_TABLE']),
    DYNAMODB_UPLOADS_TABLE=$($config['DYNAMODB_UPLOADS_TABLE']),
    DYNAMODB_TRANSACTIONS_TABLE=$($config['DYNAMODB_TRANSACTIONS_TABLE']),
    S3_UPLOADS_BUCKET=$($config['S3_UPLOADS_BUCKET']),
    BEDROCK_MODEL_ID=$($config['BEDROCK_MODEL_ID'])
}
"@

# Create AuthHandler Lambda
Write-SubStep "Deploying AuthHandler Lambda"
$existingAuth = aws lambda get-function --function-name $config['LAMBDA_AUTH_FUNCTION'] 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Success "AuthHandler exists, updating code..."
    if (-not $DryRun) {
        aws lambda update-function-code `
            --function-name $config['LAMBDA_AUTH_FUNCTION'] `
            --s3-bucket $config['S3_LAMBDA_BUCKET'] `
            --s3-key lambda-auth-1.0.0.jar 2>&1 | Out-Null

        $authInfo = aws lambda get-function --function-name $config['LAMBDA_AUTH_FUNCTION'] --output json | ConvertFrom-Json
        $script:authLambdaArn = $authInfo.Configuration.FunctionArn
        Write-Success "Updated AuthHandler: $script:authLambdaArn"
    }
} elseif (-not $DryRun) {
    $result = aws lambda create-function `
        --function-name $config['LAMBDA_AUTH_FUNCTION'] `
        --runtime java17 `
        --role $script:roleArn `
        --handler com.example.lambda.AuthHandler::handleRequest `
        --code S3Bucket=$($config['S3_LAMBDA_BUCKET']),S3Key=lambda-auth-1.0.0.jar `
        --timeout $config['LAMBDA_TIMEOUT'] `
        --memory-size $config['LAMBDA_MEMORY_SIZE'] `
        --environment $authEnvVars `
        --output json | ConvertFrom-Json

    $script:authLambdaArn = $result.FunctionArn
    Write-Success "Created AuthHandler: $script:authLambdaArn"
}

# Create UploadHandler Lambda
Write-SubStep "Deploying UploadHandler Lambda"
$existingUpload = aws lambda get-function --function-name $config['LAMBDA_UPLOAD_FUNCTION'] 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Success "UploadHandler exists, updating code..."
    if (-not $DryRun) {
        aws lambda update-function-code `
            --function-name $config['LAMBDA_UPLOAD_FUNCTION'] `
            --s3-bucket $config['S3_LAMBDA_BUCKET'] `
            --s3-key lambda-auth-1.0.0.jar 2>&1 | Out-Null

        $uploadInfo = aws lambda get-function --function-name $config['LAMBDA_UPLOAD_FUNCTION'] --output json | ConvertFrom-Json
        $script:uploadLambdaArn = $uploadInfo.Configuration.FunctionArn
        Write-Success "Updated UploadHandler: $script:uploadLambdaArn"
    }
} elseif (-not $DryRun) {
    $result = aws lambda create-function `
        --function-name $config['LAMBDA_UPLOAD_FUNCTION'] `
        --runtime java17 `
        --role $script:roleArn `
        --handler com.example.lambda.AuthenticatedUploadHandler::handleRequest `
        --code S3Bucket=$($config['S3_LAMBDA_BUCKET']),S3Key=lambda-auth-1.0.0.jar `
        --timeout $config['LAMBDA_TIMEOUT'] `
        --memory-size $config['LAMBDA_MEMORY_SIZE'] `
        --environment $uploadEnvVars `
        --output json | ConvertFrom-Json

    $script:uploadLambdaArn = $result.FunctionArn
    Write-Success "Created UploadHandler: $script:uploadLambdaArn"
}

# Step 7: Create API Gateway
Write-Step "Step 7/9: Setting Up API Gateway"

# Check if API exists
$apis = aws apigateway get-rest-apis --output json | ConvertFrom-Json
$existingApi = $apis.items | Where-Object { $_.name -eq $config['API_GATEWAY_NAME'] } | Select-Object -First 1

if ($existingApi) {
    $script:apiId = $existingApi.id
    Write-Success "API Gateway exists: $script:apiId"
} elseif (-not $DryRun) {
    Write-Info "Creating REST API..."
    $result = aws apigateway create-rest-api `
        --name $config['API_GATEWAY_NAME'] `
        --description "Guide-ya API" `
        --endpoint-configuration types=REGIONAL `
        --output json | ConvertFrom-Json

    $script:apiId = $result.id
    Write-Success "Created API: $script:apiId"
}

if ($script:apiId -and -not $DryRun) {
    # Get root resource
    $resources = aws apigateway get-resources --rest-api-id $script:apiId --output json | ConvertFrom-Json
    $rootId = $resources.items[0].id

    Write-SubStep "Creating API resources and methods"

    # Create /auth resource
    $authResource = aws apigateway create-resource `
        --rest-api-id $script:apiId `
        --parent-id $rootId `
        --path-part auth `
        --output json 2>&1

    if ($LASTEXITCODE -ne 0) {
        # Resource might exist, get it
        $resources = aws apigateway get-resources --rest-api-id $script:apiId --output json | ConvertFrom-Json
        $authResourceId = ($resources.items | Where-Object { $_.path -eq '/auth' }).id
    } else {
        $authResourceId = ($authResource | ConvertFrom-Json).id
    }

    Write-Success "Auth resource: $authResourceId"

    # Create /upload resource
    $uploadResource = aws apigateway create-resource `
        --rest-api-id $script:apiId `
        --parent-id $rootId `
        --path-part upload `
        --output json 2>&1

    if ($LASTEXITCODE -ne 0) {
        $resources = aws apigateway get-resources --rest-api-id $script:apiId --output json | ConvertFrom-Json
        $uploadResourceId = ($resources.items | Where-Object { $_.path -eq '/upload' }).id
    } else {
        $uploadResourceId = ($uploadResource | ConvertFrom-Json).id
    }

    Write-Success "Upload resource: $uploadResourceId"

    # Create Cognito Authorizer
    Write-SubStep "Creating Cognito authorizer"
    $authorizers = aws apigateway get-authorizers --rest-api-id $script:apiId --output json | ConvertFrom-Json
    $existingAuth = $authorizers.items | Where-Object { $_.name -eq 'CognitoAuthorizer' } | Select-Object -First 1

    if ($existingAuth) {
        $authorizerId = $existingAuth.id
        Write-Success "Authorizer exists: $authorizerId"
    } else {
        $authResult = aws apigateway create-authorizer `
            --rest-api-id $script:apiId `
            --name CognitoAuthorizer `
            --type COGNITO_USER_POOLS `
            --provider-arns "arn:aws:cognito-idp:$($config['AWS_REGION']):$($config['AWS_ACCOUNT_ID']):userpool/$script:userPoolId" `
            --identity-source 'method.request.header.Authorization' `
            --output json | ConvertFrom-Json

        $authorizerId = $authResult.id
        Write-Success "Created authorizer: $authorizerId"
    }

    # Create POST /auth method (no auth required)
    Write-SubStep "Creating POST /auth method"
    aws apigateway put-method `
        --rest-api-id $script:apiId `
        --resource-id $authResourceId `
        --http-method POST `
        --authorization-type NONE `
        --no-api-key-required 2>&1 | Out-Null

    # Link /auth to AuthHandler Lambda
    $authUri = "arn:aws:apigateway:$($config['AWS_REGION']):lambda:path/2015-03-31/functions/$script:authLambdaArn/invocations"

    aws apigateway put-integration `
        --rest-api-id $script:apiId `
        --resource-id $authResourceId `
        --http-method POST `
        --type AWS_PROXY `
        --integration-http-method POST `
        --uri $authUri 2>&1 | Out-Null

    # Grant API Gateway permission to invoke AuthHandler
    aws lambda add-permission `
        --function-name $config['LAMBDA_AUTH_FUNCTION'] `
        --statement-id apigateway-auth `
        --action lambda:InvokeFunction `
        --principal apigateway.amazonaws.com `
        --source-arn "arn:aws:execute-api:$($config['AWS_REGION']):$($config['AWS_ACCOUNT_ID']):$script:apiId/*/*/auth" 2>&1 | Out-Null

    Write-Success "POST /auth configured"

    # Create POST /upload method (with Cognito auth)
    Write-SubStep "Creating POST /upload method"
    aws apigateway put-method `
        --rest-api-id $script:apiId `
        --resource-id $uploadResourceId `
        --http-method POST `
        --authorization-type COGNITO_USER_POOLS `
        --authorizer-id $authorizerId `
        --no-api-key-required 2>&1 | Out-Null

    # Link /upload to UploadHandler Lambda
    $uploadUri = "arn:aws:apigateway:$($config['AWS_REGION']):lambda:path/2015-03-31/functions/$script:uploadLambdaArn/invocations"

    aws apigateway put-integration `
        --rest-api-id $script:apiId `
        --resource-id $uploadResourceId `
        --http-method POST `
        --type AWS_PROXY `
        --integration-http-method POST `
        --uri $uploadUri 2>&1 | Out-Null

    # Grant API Gateway permission to invoke UploadHandler
    aws lambda add-permission `
        --function-name $config['LAMBDA_UPLOAD_FUNCTION'] `
        --statement-id apigateway-upload `
        --action lambda:InvokeFunction `
        --principal apigateway.amazonaws.com `
        --source-arn "arn:aws:execute-api:$($config['AWS_REGION']):$($config['AWS_ACCOUNT_ID']):$script:apiId/*/*/upload" 2>&1 | Out-Null

    Write-Success "POST /upload configured"

    # Enable CORS
    Write-SubStep "Enabling CORS"

    # OPTIONS /auth
    aws apigateway put-method `
        --rest-api-id $script:apiId `
        --resource-id $authResourceId `
        --http-method OPTIONS `
        --authorization-type NONE 2>&1 | Out-Null

    aws apigateway put-integration `
        --rest-api-id $script:apiId `
        --resource-id $authResourceId `
        --http-method OPTIONS `
        --type MOCK `
        --request-templates '{"application/json": "{\"statusCode\": 200}"}' 2>&1 | Out-Null

    aws apigateway put-method-response `
        --rest-api-id $script:apiId `
        --resource-id $authResourceId `
        --http-method OPTIONS `
        --status-code 200 `
        --response-parameters 'method.response.header.Access-Control-Allow-Headers=false,method.response.header.Access-Control-Allow-Methods=false,method.response.header.Access-Control-Allow-Origin=false' 2>&1 | Out-Null

    aws apigateway put-integration-response `
        --rest-api-id $script:apiId `
        --resource-id $authResourceId `
        --http-method OPTIONS `
        --status-code 200 `
        --response-parameters '{\"method.response.header.Access-Control-Allow-Headers\":\"'"'"'Content-Type,Authorization'"'"'\",\"method.response.header.Access-Control-Allow-Methods\":\"'"'"'POST,OPTIONS'"'"'\",\"method.response.header.Access-Control-Allow-Origin\":\"'"'"'*'"'"'\"}' 2>&1 | Out-Null

    # OPTIONS /upload
    aws apigateway put-method `
        --rest-api-id $script:apiId `
        --resource-id $uploadResourceId `
        --http-method OPTIONS `
        --authorization-type NONE 2>&1 | Out-Null

    aws apigateway put-integration `
        --rest-api-id $script:apiId `
        --resource-id $uploadResourceId `
        --http-method OPTIONS `
        --type MOCK `
        --request-templates '{"application/json": "{\"statusCode\": 200}"}' 2>&1 | Out-Null

    aws apigateway put-method-response `
        --rest-api-id $script:apiId `
        --resource-id $uploadResourceId `
        --http-method OPTIONS `
        --status-code 200 `
        --response-parameters 'method.response.header.Access-Control-Allow-Headers=false,method.response.header.Access-Control-Allow-Methods=false,method.response.header.Access-Control-Allow-Origin=false' 2>&1 | Out-Null

    aws apigateway put-integration-response `
        --rest-api-id $script:apiId `
        --resource-id $uploadResourceId `
        --http-method OPTIONS `
        --status-code 200 `
        --response-parameters '{\"method.response.header.Access-Control-Allow-Headers\":\"'"'"'Content-Type,Authorization'"'"'\",\"method.response.header.Access-Control-Allow-Methods\":\"'"'"'POST,OPTIONS'"'"'\",\"method.response.header.Access-Control-Allow-Origin\":\"'"'"'*'"'"'\"}' 2>&1 | Out-Null

    Write-Success "CORS enabled"

    # Deploy to prod stage
    Write-SubStep "Deploying to $($config['API_GATEWAY_STAGE']) stage"
    aws apigateway create-deployment `
        --rest-api-id $script:apiId `
        --stage-name $config['API_GATEWAY_STAGE'] `
        --description "Automated deployment" 2>&1 | Out-Null

    $script:apiUrl = "https://$script:apiId.execute-api.$($config['AWS_REGION']).amazonaws.com/$($config['API_GATEWAY_STAGE'])"
    Write-Success "Deployed to: $script:apiUrl"
}

# Step 8: Validation
Write-Step "Step 8/9: Validating Deployment"

if (-not $DryRun) {
    Write-Info "Checking Lambda functions..."
    $authCheck = aws lambda get-function --function-name $config['LAMBDA_AUTH_FUNCTION'] 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Success "AuthHandler is deployed"
    } else {
        Write-Error "AuthHandler validation failed"
    }

    $uploadCheck = aws lambda get-function --function-name $config['LAMBDA_UPLOAD_FUNCTION'] 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Success "UploadHandler is deployed"
    } else {
        Write-Error "UploadHandler validation failed"
    }

    Write-Info "Checking API Gateway..."
    if ($script:apiUrl) {
        Write-Success "API Gateway is live at: $script:apiUrl"
    } else {
        Write-Warning "API URL not set"
    }

    Write-Info "Checking DynamoDB tables..."
    $tables = aws dynamodb list-tables --output json | ConvertFrom-Json
    $expectedTables = @($config['DYNAMODB_USERS_TABLE'], $config['DYNAMODB_UPLOADS_TABLE'], $config['DYNAMODB_TRANSACTIONS_TABLE'])

    foreach ($table in $expectedTables) {
        if ($tables.TableNames -contains $table) {
            Write-Success "$table exists"
        } else {
            Write-Error "$table not found"
        }
    }
} else {
    Write-Warning "Skipping validation (dry run mode)"
}

# Step 9: Output Configuration
Write-Step "Step 9/9: Deployment Complete!"

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "     DEPLOYMENT SUCCESSFUL!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green

if (-not $DryRun) {
    Write-Host ""
    Write-Host "API Configuration:" -ForegroundColor Cyan
    Write-Host "  API URL: $script:apiUrl" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Cognito Configuration:" -ForegroundColor Cyan
    Write-Host "  User Pool ID: $script:userPoolId" -ForegroundColor Yellow
    Write-Host "  Client ID: $script:clientId" -ForegroundColor Yellow

    Write-Host ""
    Write-Host "API Endpoints:" -ForegroundColor Cyan
    Write-Host "  POST $script:apiUrl/auth (signup, login, confirm)" -ForegroundColor Yellow
    Write-Host "  POST $script:apiUrl/upload (requires JWT)" -ForegroundColor Yellow

    Write-Host ""
    Write-Host "Next Steps:" -ForegroundColor Cyan
    Write-Host "  1. Update desktop app Config.java:" -ForegroundColor Gray
    $apiUrlLine = "     public static final String API_BASE_URL = ""$script:apiUrl"";"
    Write-Host $apiUrlLine -ForegroundColor White
    Write-Host "     public static final boolean DEMO_MODE = false;" -ForegroundColor White
    Write-Host ""
    Write-Host "  2. Rebuild desktop app:" -ForegroundColor Gray
    Write-Host "     cd $projectRoot\desktop-client" -ForegroundColor White
    Write-Host "     mvn clean package" -ForegroundColor White
    Write-Host ""
    Write-Host "  3. Run desktop app:" -ForegroundColor Gray
    Write-Host "     java -jar target\bankbuddy-desktop-1.0.0.jar" -ForegroundColor White
    Write-Host ""
    Write-Host "  4. Test signup and login!" -ForegroundColor Gray

    # Save configuration to file
    $configOutput = @{
        ApiUrl = $script:apiUrl
        UserPoolId = $script:userPoolId
        ClientId = $script:clientId
        Region = $config['AWS_REGION']
        DeploymentDate = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    } | ConvertTo-Json

    $configOutput | Out-File -FilePath "$PSScriptRoot\deployment-output.json" -Encoding UTF8
    Write-Host ""
    Write-Host "Configuration saved to: $PSScriptRoot\deployment-output.json" -ForegroundColor Gray
} else {
    Write-Host ""
    Write-Host "DRY RUN COMPLETE" -ForegroundColor Yellow
    Write-Host "Run without -DryRun flag to deploy" -ForegroundColor Gray
}

Write-Host ""
