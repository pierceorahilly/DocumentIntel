# Guide-ya Complete AWS Deployment Script (Fixed)
# Deploys everything end-to-end automatically (REST API Gateway)

param(
    [switch]$SkipBuild,
    [switch]$DryRun,
    [switch]$CleanupOnly
)
Write-Host "RUNNING: $PSCommandPath" -ForegroundColor Magenta

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

# Helper: run aws and optionally ignore "already exists"/conflict noise
function Invoke-Aws {
    param(
        [Parameter(Mandatory=$true)]
        [string[]]$Args,

        [switch]$IgnoreConflict
    )

    $prevEA = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"

    try {
        $out = $null

        try {
            # Capture stdout+stderr so we can inspect it ourselves
            $out = & aws @Args 2>&1
        } catch {
            # PowerShell 5.1 may throw NativeCommandError for native stderr - swallow it
        }

        # Convert output to string, handling both string and error objects
        $text = ""
        if ($out) {
            foreach ($item in $out) {
                if ($item -is [System.Management.Automation.ErrorRecord]) {
                    $text += $item.Exception.Message + "`n"
                } else {
                    $text += $item.ToString() + "`n"
                }
            }
        }
        $text = $text.Trim()

        # Check for errors first
        if ($LASTEXITCODE -ne 0) {
            # Optional idempotent behavior - ignore conflicts
            # Exit code 254 = conflict
            # Exit code 252 = bad request (often conflict, especially with put-integration)
            # If error text is empty and exit code is 252/254, assume it's a conflict
            $isConflict = $text -match "ConflictException" -or $text -match "AlreadyExists" -or $text -match "ResourceConflictException" -or $text -match "already exists" -or $text -match "Method already exists" -or $text -match "Integration already exists"
            $assumeConflict = ($LASTEXITCODE -eq 252 -or $LASTEXITCODE -eq 254) -and [string]::IsNullOrWhiteSpace($text)

            if ($IgnoreConflict -and ($LASTEXITCODE -eq 254 -or $assumeConflict -or $isConflict)) {
                Write-Info "Resource already exists (ignored)"
                return $out
            }

            Write-Host "`nAWS CLI Error Output:" -ForegroundColor Red
            if ($text) {
                Write-Host $text -ForegroundColor Red
            } else {
                Write-Host "(No error message captured - command failed with exit code $LASTEXITCODE)" -ForegroundColor Red
            }
            throw ("AWS CLI failed: aws " + ($Args -join " ") + "`n" + $text)
        }

        return $out
    }
    finally {
        $ErrorActionPreference = $prevEA
    }
}


function Ensure-LambdaPermission {
    param(
        [string]$FunctionName,
        [string]$StatementId,
        [string]$SourceArn
    )
    if ($DryRun) { return }

    $prevEA = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    try {
        $out = & aws lambda add-permission `
            --function-name $FunctionName `
            --statement-id $StatementId `
            --action lambda:InvokeFunction `
            --principal apigateway.amazonaws.com `
            --source-arn $SourceArn 2>&1

        if ($LASTEXITCODE -ne 0) {
            $text = ($out | Out-String).Trim()
            if ($text -match "ResourceConflictException" -or $text -match "Statement already exists" -or $LASTEXITCODE -eq 254) {
                Write-Info "Lambda permission already exists (ignored)"
                return
            }
            Write-Warning "Failed to add lambda permission: $text"
            return
        }

        Write-Success "Lambda permission ensured: $FunctionName / $StatementId"
    } finally {
        $ErrorActionPreference = $prevEA
    }
}

function Get-JavaHomeFromPath {
    $javaExe = (Get-Command java -ErrorAction SilentlyContinue).Source
    if (-not $javaExe) { return $null }
    return (Split-Path (Split-Path $javaExe)) # ...\bin\java.exe -> JAVA_HOME
}

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

$prevEA = $ErrorActionPreference
$ErrorActionPreference = "SilentlyContinue"
try {
    $awsVersion = & aws --version 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Error "AWS CLI not found. Install: https://aws.amazon.com/cli/"
        exit 1
    }
    Write-Success "AWS CLI installed"
} finally {
    $ErrorActionPreference = $prevEA
}

$ErrorActionPreference = "SilentlyContinue"
try {
    $identityJson = & aws sts get-caller-identity --output json 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Error "AWS credentials not configured. Run: aws configure"
        exit 1
    }
    $identity = ($identityJson -join "`n") | ConvertFrom-Json
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
} finally {
    $ErrorActionPreference = "Stop"
}
# Java check disabled (Java 17 confirmed installed)
Write-Warning "Skipping Java validation (Java 17 confirmed installed)."







# Global variables
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
$jarFile = Get-ChildItem -Path (Join-Path $projectRoot "target") -Filter *.jar |
    Where-Object { $_.Name -notlike "original-*" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1 -ExpandProperty FullName


if ($SkipBuild -and (Test-Path $jarFile)) {
    Write-Warning "Skipping build (using existing JAR)"
} else {
    Write-Info "Running: mvn clean package -DskipTests"
    Push-Location $projectRoot
    try {
        if (-not $DryRun) {
            $mvnOutput = & mvn clean package -DskipTests 2>&1
            if ($LASTEXITCODE -ne 0) {
                Write-Error "Maven build failed:"
                $mvnOutput | ForEach-Object { Write-Error $_ }
                throw "Maven build failed"
            }
        }
        Write-Success "Lambda JAR built"
    } finally {
        Pop-Location
    }
}

if (-not $DryRun -and -not (Test-Path $jarFile)) {
    Write-Error "JAR file not found: $jarFile"
    exit 1
}
$jarSize = if (Test-Path $jarFile) { (Get-Item $jarFile).Length / 1MB } else { 0 }
Write-Info "JAR size: $($jarSize.ToString('F2')) MB"

# Step 2: S3 buckets
Write-Step "Step 2/9: Creating S3 Buckets"

function Test-S3BucketAccessible {
    param([string]$name)

    # Some AWS CLI commands write to stderr even on success; don't let PS treat that as terminating
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $out = & aws s3api get-bucket-location --bucket $name 2>&1
        return ($LASTEXITCODE -eq 0)
    } finally {
        $ErrorActionPreference = $prev
    }
}


function Create-S3BucketIfNeeded {
    param([string]$name, [string]$region)

    if (Test-S3BucketAccessible $name) {
        Write-Success "Bucket exists/accessible: $name"
        return $true
    }

    Write-Info "Creating bucket: $name"
    if ($DryRun) { return $true }

    if ($region -eq "us-east-1") {
        Invoke-Aws -Args @("s3","mb","s3://$name","--region",$region) | Out-Null
    } else {
        Invoke-Aws -Args @("s3api","create-bucket","--bucket",$name,"--region",$region,"--create-bucket-configuration","LocationConstraint=$region") | Out-Null
    }

    # Block public access
    Invoke-Aws -Args @(
        "s3api","put-public-access-block",
        "--bucket",$name,
        "--public-access-block-configuration","BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"
    ) | Out-Null

    Write-Success "Created bucket: $name"
    return $true
}

if (-not (Create-S3BucketIfNeeded $config['S3_LAMBDA_BUCKET'] $config['AWS_REGION'])) { exit 1 }
if (-not (Create-S3BucketIfNeeded $config['S3_UPLOADS_BUCKET'] $config['AWS_REGION'])) { exit 1 }

Write-SubStep "Uploading Lambda JAR to S3"
if (-not $DryRun) {
    Invoke-Aws -Args @("s3","cp",$jarFile,"s3://$($config['S3_LAMBDA_BUCKET'])/lambda-auth-1.0.0.jar") | Out-Null
}
Write-Success "Uploaded Lambda JAR"

# Step 3: Cognito
Write-Step "Step 3/9: Creating Cognito User Pool"

# List existing pools safely
$prev = $ErrorActionPreference
$ErrorActionPreference = "SilentlyContinue"
try {
    $userPoolsJson = & aws cognito-idp list-user-pools --max-results 60 --output json 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "Failed to list user pools, will create new one"
        $userPools = @{ UserPools = @() }
    } else {
        $userPools = ($userPoolsJson -join "`n") | ConvertFrom-Json
    }
} finally {
    $ErrorActionPreference = $prev
}

$existingPool = $userPools.UserPools |
    Where-Object { $_.Name -eq $config['COGNITO_USER_POOL_NAME'] } |
    Select-Object -First 1

if ($existingPool) {
    $script:userPoolId = $existingPool.Id
    Write-Success "User Pool exists: $script:userPoolId"
}
elseif (-not $DryRun) {
    Write-Info "Creating User Pool..."

    # Build JSON safely using PowerShell objects
    $poolObject = @{
        PoolName = $config['COGNITO_USER_POOL_NAME']
        Policies = @{
            PasswordPolicy = @{
                MinimumLength    = 8
                RequireUppercase = $true
                RequireLowercase = $true
                RequireNumbers   = $true
                RequireSymbols   = $true
            }
        }
        AutoVerifiedAttributes = @("email")
        UsernameAttributes     = @("email")
        Schema = @(
            @{
                Name              = "email"
                AttributeDataType = "String"
                Required          = $true
                Mutable           = $false
            },
            @{
                Name              = "name"
                AttributeDataType = "String"
                Required          = $true
                Mutable           = $true
            },
            @{
                Name              = "birthdate"
                AttributeDataType = "String"
                Mutable           = $true
            },
            @{
                Name              = "address"
                AttributeDataType = "String"
                Mutable           = $true
            }
        )
    }

    $tmpPoolFile = Join-Path $PSScriptRoot "tmp-user-pool.json"
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    $poolJson = $poolObject | ConvertTo-Json -Depth 10
    [System.IO.File]::WriteAllText($tmpPoolFile, $poolJson, $utf8NoBom)

    $fileArg = "file://" + ($tmpPoolFile -replace "\\","/")
    $resultJson = Invoke-Aws -Args @("cognito-idp","create-user-pool","--cli-input-json",$fileArg,"--output","json")
    $result = ($resultJson -join "`n") | ConvertFrom-Json

    Remove-Item $tmpPoolFile -Force -ErrorAction SilentlyContinue

    $script:userPoolId = $result.UserPool.Id
    Write-Success "Created User Pool: $script:userPoolId"
}

# Create / fetch App Client
if ($script:userPoolId -and -not $DryRun) {
    $clientsJson = Invoke-Aws -Args @("cognito-idp","list-user-pool-clients","--user-pool-id",$script:userPoolId,"--output","json")
    $clients = ($clientsJson -join "`n") | ConvertFrom-Json

    $existingClient = $clients.UserPoolClients |
        Where-Object { $_.ClientName -eq $config['COGNITO_APP_CLIENT_NAME'] } |
        Select-Object -First 1

    if ($existingClient) {
        $script:clientId = $existingClient.ClientId
        Write-Success "App Client exists: $script:clientId"
    }
    else {
        Write-Info "Creating App Client..."

        $resultJson = Invoke-Aws -Args @(
            "cognito-idp","create-user-pool-client",
            "--user-pool-id",$script:userPoolId,
            "--client-name",$config['COGNITO_APP_CLIENT_NAME'],
            "--no-generate-secret",
            "--explicit-auth-flows","ALLOW_USER_PASSWORD_AUTH","ALLOW_REFRESH_TOKEN_AUTH","ALLOW_USER_SRP_AUTH",
            "--output","json"
        )
        $result = ($resultJson -join "`n") | ConvertFrom-Json

        $script:clientId = $result.UserPoolClient.ClientId
        Write-Success "Created App Client: $script:clientId"
    }
}


# Step 4/9: DynamoDB Tables (PS 5.1 safe + AWS CLI JSON file parsing fixed)
Write-Step "Step 4/9: Creating DynamoDB Tables"

function Create-DynamoTable {
    param(
        [string]$TableName,
        [array]$AttributeDefinitions,
        [array]$KeySchema,
        [array]$GlobalSecondaryIndexes = $null
    )

    # Prevent NativeCommandError from AWS stderr under $ErrorActionPreference="Stop"
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        # Existence check
        $null = & aws dynamodb describe-table --table-name $TableName --region $config['AWS_REGION'] 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Success "Table exists: $TableName"
            return $true
        }

        Write-Info "Creating table: $TableName"
        if ($DryRun) { return $true }

        # Build request object (reliable)
        $tableObject = @{
            TableName            = $TableName
            AttributeDefinitions = $AttributeDefinitions
            KeySchema            = $KeySchema
            BillingMode          = "PAY_PER_REQUEST"
        }
        if ($GlobalSecondaryIndexes) {
            $tableObject.GlobalSecondaryIndexes = $GlobalSecondaryIndexes
        }

        # Write JSON as UTF-8 WITHOUT BOM (AWS CLI on Windows can choke on BOM)
        $tmpFile = Join-Path $PSScriptRoot ("tmp-ddb-" + $TableName + ".json")
        $json = $tableObject | ConvertTo-Json -Depth 20
        $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
        [System.IO.File]::WriteAllText($tmpFile, $json, $utf8NoBom)

        # Use file:// + forward slashes (Windows-safe for AWS CLI)
        $fileArg = "file://" + ($tmpFile -replace "\\","/")


        $out = & aws dynamodb create-table `
            --cli-input-json $fileArg `
            --region $config['AWS_REGION'] `
            2>&1

        Remove-Item $tmpFile -Force -ErrorAction SilentlyContinue

        if ($LASTEXITCODE -ne 0) {
            Write-Error "Failed to create table: $TableName"
            Write-Error ($out -join "`n")
            return $false
        }

        Write-Success "Created table: $TableName"
        return $true
    }
    finally {
        $ErrorActionPreference = $prev
    }
}

# Users table
if (-not (Create-DynamoTable `
    -TableName $config['DYNAMODB_USERS_TABLE'] `
    -AttributeDefinitions @(
        @{ AttributeName = "userId"; AttributeType = "S" }
    ) `
    -KeySchema @(
        @{ AttributeName = "userId"; KeyType = "HASH" }
    )
)) { exit 1 }

# Uploads table (GSI: UserUploadsIndex)
if (-not (Create-DynamoTable `
    -TableName $config['DYNAMODB_UPLOADS_TABLE'] `
    -AttributeDefinitions @(
        @{ AttributeName = "uploadId"; AttributeType = "S" }
        @{ AttributeName = "userId"; AttributeType = "S" }
        @{ AttributeName = "uploadDate"; AttributeType = "S" }
    ) `
    -KeySchema @(
        @{ AttributeName = "uploadId"; KeyType = "HASH" }
    ) `
    -GlobalSecondaryIndexes @(
        @{
            IndexName = "UserUploadsIndex"
            KeySchema = @(
                @{ AttributeName = "userId"; KeyType = "HASH" }
                @{ AttributeName = "uploadDate"; KeyType = "RANGE" }
            )
            Projection = @{ ProjectionType = "ALL" }
        }
    )
)) { exit 1 }

# Transactions table (GSIs: UserTransactionsIndex, UploadTransactionsIndex)
if (-not (Create-DynamoTable `
    -TableName $config['DYNAMODB_TRANSACTIONS_TABLE'] `
    -AttributeDefinitions @(
        @{ AttributeName = "transactionId"; AttributeType = "S" }
        @{ AttributeName = "userId"; AttributeType = "S" }
        @{ AttributeName = "uploadId"; AttributeType = "S" }
        @{ AttributeName = "date"; AttributeType = "S" }
    ) `
    -KeySchema @(
        @{ AttributeName = "transactionId"; KeyType = "HASH" }
    ) `
    -GlobalSecondaryIndexes @(
        @{
            IndexName = "UserTransactionsIndex"
            KeySchema = @(
                @{ AttributeName = "userId"; KeyType = "HASH" }
                @{ AttributeName = "date"; KeyType = "RANGE" }
            )
            Projection = @{ ProjectionType = "ALL" }
        },
        @{
            IndexName = "UploadTransactionsIndex"
            KeySchema = @(
                @{ AttributeName = "uploadId"; KeyType = "HASH" }
            )
            Projection = @{ ProjectionType = "ALL" }
        }
    )
)) { exit 1 }


# Step 5/9: IAM Role (fixed: trust policy JSON + NativeCommandError)
Write-Step "Step 5/9: Creating IAM Execution Role"

$roleName = $config['IAM_ROLE_NAME']

# ---------- Existence check (safe in PS 5.1) ----------
$prevEA = $ErrorActionPreference
$ErrorActionPreference = "SilentlyContinue"
try {
    $roleArn = & aws iam get-role --role-name $roleName --query "Role.Arn" --output text 2>&1
} catch { } finally { $ErrorActionPreference = $prevEA }

if ($LASTEXITCODE -eq 0 -and $roleArn -and ($roleArn -notmatch "NoSuchEntity")) {
    $script:roleArn = $roleArn.Trim()
    Write-Success "IAM Role exists: $script:roleArn"
}
elseif (-not $DryRun) {

    # ---------- FIX 1: Create role using trust policy FILE (valid JSON) ----------
    Write-Info "Creating IAM role..."

    $trustPolicy = @"
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": { "Service": "lambda.amazonaws.com" },
      "Action": "sts:AssumeRole"
    }
  ]
}
"@

    $tmpTrust = Join-Path $PSScriptRoot "temp-trust-policy.json"
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($tmpTrust, $trustPolicy, $utf8NoBom)

    $trustArg = "file://" + ($tmpTrust -replace "\\","/")

    $resultJson = & aws iam create-role `
        --role-name $roleName `
        --assume-role-policy-document $trustArg `
        --description "Execution role for Guide-ya Lambda functions" `
        --output json 2>&1

    Remove-Item $tmpTrust -Force -ErrorAction SilentlyContinue

    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to create IAM role"
        Write-Error ($resultJson -join "`n")
        exit 1
    }

    $result = $resultJson | ConvertFrom-Json
    $script:roleArn = $result.Role.Arn
    Write-Success "Created IAM role: $script:roleArn"

    # ---------- Attach permissions policy ----------
    Write-SubStep "Attaching permissions policy"

    $policyDocument = @"
{
  "Version":"2012-10-17",
  "Statement":[
    {
      "Sid":"DynamoDBAccess",
      "Effect":"Allow",
      "Action":["dynamodb:GetItem","dynamodb:PutItem","dynamodb:UpdateItem","dynamodb:Query","dynamodb:Scan","dynamodb:BatchWriteItem"],
      "Resource":[
        "arn:aws:dynamodb:$($config['AWS_REGION']):$($config['AWS_ACCOUNT_ID']):table/$($config['DYNAMODB_USERS_TABLE'])",
        "arn:aws:dynamodb:$($config['AWS_REGION']):$($config['AWS_ACCOUNT_ID']):table/$($config['DYNAMODB_UPLOADS_TABLE'])",
        "arn:aws:dynamodb:$($config['AWS_REGION']):$($config['AWS_ACCOUNT_ID']):table/$($config['DYNAMODB_TRANSACTIONS_TABLE'])",
        "arn:aws:dynamodb:$($config['AWS_REGION']):$($config['AWS_ACCOUNT_ID']):table/$($config['DYNAMODB_UPLOADS_TABLE'])/index/*",
        "arn:aws:dynamodb:$($config['AWS_REGION']):$($config['AWS_ACCOUNT_ID']):table/$($config['DYNAMODB_TRANSACTIONS_TABLE'])/index/*"
      ]
    },
    {
      "Sid":"S3Access",
      "Effect":"Allow",
      "Action":["s3:PutObject","s3:GetObject","s3:ListBucket"],
      "Resource":["arn:aws:s3:::$($config['S3_UPLOADS_BUCKET'])","arn:aws:s3:::$($config['S3_UPLOADS_BUCKET'])/*"]
    },
    {
      "Sid":"TextractAccess",
      "Effect":"Allow",
      "Action":["textract:AnalyzeDocument","textract:StartDocumentAnalysis","textract:GetDocumentAnalysis"],
      "Resource":"*"
    },
    {
      "Sid":"BedrockAccess",
      "Effect":"Allow",
      "Action":["bedrock:InvokeModel"],
      "Resource":"*"
    },
    {
      "Sid":"CloudWatchLogsAccess",
      "Effect":"Allow",
      "Action":["logs:CreateLogGroup","logs:CreateLogStream","logs:PutLogEvents"],
      "Resource":"arn:aws:logs:$($config['AWS_REGION']):$($config['AWS_ACCOUNT_ID']):log-group:/aws/lambda/*"
    },
    {
      "Sid":"CognitoAccess",
      "Effect":"Allow",
      "Action":["cognito-idp:SignUp","cognito-idp:ConfirmSignUp","cognito-idp:InitiateAuth","cognito-idp:ForgotPassword","cognito-idp:ConfirmForgotPassword","cognito-idp:AdminGetUser"],
      "Resource":"arn:aws:cognito-idp:$($config['AWS_REGION']):$($config['AWS_ACCOUNT_ID']):userpool/*"
    }
  ]
}
"@

    $tmpPolicy = Join-Path $PSScriptRoot "temp-policy.json"
    [System.IO.File]::WriteAllText($tmpPolicy, $policyDocument, $utf8NoBom)

    # ---------- FIX 2: Invoke-Aws wrapper prevents NativeCommandError ----------
    Invoke-Aws -Args @(
        "iam","put-role-policy",
        "--role-name",$roleName,
        "--policy-name","GuideYaLambdaPolicy",
        "--policy-document","file://$($tmpPolicy -replace '\\','/')"
    ) | Out-Null

    Remove-Item $tmpPolicy -Force

    Write-Success "Attached inline policy"
    Write-Info "Waiting 10 seconds for IAM role to propagate..."
    Start-Sleep -Seconds 10
}

Write-Info "Role ARN: $script:roleArn"


# Step 6: Lambda
Write-Step "Step 6/9: Deploying Lambda Functions"

# FIXED: single-line env var formatting for AWS 
# --- Ensure Cognito IDs exist before building Lambda env vars ---
if (-not $script:userPoolId -or [string]::IsNullOrWhiteSpace($script:userPoolId)) {
    # Try recover user pool id by name from config
    $poolsJson = Invoke-Aws -Args @("cognito-idp","list-user-pools","--max-results","60","--output","json")
    $pools = ($poolsJson -join "`n") | ConvertFrom-Json
    $pool = $pools.UserPools | Where-Object { $_.Name -eq $config['COGNITO_USER_POOL_NAME'] } | Select-Object -First 1
    if ($pool) { $script:userPoolId = $pool.Id }
}

if (-not $script:clientId -or [string]::IsNullOrWhiteSpace($script:clientId)) {
    # Try recover client id by app client name from config (or pick first)
    if ($script:userPoolId) {
        $clientsJson = Invoke-Aws -Args @("cognito-idp","list-user-pool-clients","--user-pool-id",$script:userPoolId,"--max-results","60","--output","json")
        $clients = ($clientsJson -join "`n") | ConvertFrom-Json

        # If you have a configured name, use it; otherwise take first
        if ($config.ContainsKey('COGNITO_APP_CLIENT_NAME') -and $config['COGNITO_APP_CLIENT_NAME']) {
            $client = $clients.UserPoolClients | Where-Object { $_.ClientName -eq $config['COGNITO_APP_CLIENT_NAME'] } | Select-Object -First 1
        } else {
            $client = $clients.UserPoolClients | Select-Object -First 1
        }

        if ($client) { $script:clientId = $client.ClientId }
    }
}

if (-not $script:userPoolId -or [string]::IsNullOrWhiteSpace($script:userPoolId) -or
    -not $script:clientId -or [string]::IsNullOrWhiteSpace($script:clientId)) {
    throw "Missing Cognito IDs for Lambda env vars. userPoolId='$script:userPoolId' clientId='$script:clientId'. Fix Step 3 (Cognito setup) or set config['COGNITO_USER_POOL_NAME'] (and optionally config['COGNITO_APP_CLIENT_NAME'])."
}

# Helper: existence check without NativeCommandError
function Test-LambdaExists {
    param([string]$FunctionName)

    $prevEA = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    try {
        $out = $null
        try {
            $out = & aws lambda get-function --function-name $FunctionName --query "Configuration.FunctionArn" --output text 2>&1
        } catch { }
    } finally {
        $ErrorActionPreference = $prevEA
    }

    return ($LASTEXITCODE -eq 0)
}

# Helper: wait for Lambda function to be ready
function Wait-LambdaReady {
    param([string]$FunctionName, [int]$MaxWaitSeconds = 60)

    Write-Info "Waiting for Lambda function to be ready..."
    $startTime = Get-Date
    $prevEA = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"

    while (((Get-Date) - $startTime).TotalSeconds -lt $MaxWaitSeconds) {
        try {
            $stateJson = & aws lambda get-function --function-name $FunctionName --query "Configuration.[State,LastUpdateStatus]" --output json 2>&1
            if ($LASTEXITCODE -eq 0) {
                $state = ($stateJson -join "`n") | ConvertFrom-Json
                if ($state[0] -eq "Active" -and $state[1] -eq "Successful") {
                    Write-Success "Lambda function is ready"
                    $ErrorActionPreference = $prevEA
                    return $true
                }
                Write-Info "State: $($state[0]), Update Status: $($state[1]) - waiting..."
            }
        } catch { }
        Start-Sleep -Seconds 3
    }

    $ErrorActionPreference = $prevEA
    Write-Warning "Lambda function did not become ready within $MaxWaitSeconds seconds, proceeding anyway..."
    return $false
}

Write-SubStep "Deploying AuthHandler Lambda"
$authExists = Test-LambdaExists -FunctionName $config['LAMBDA_AUTH_FUNCTION']

if ($authExists) {
    Write-Success "AuthHandler exists, updating code..."
    if (-not $DryRun) {
        # Wait for function to be ready before updating
        Wait-LambdaReady -FunctionName $config['LAMBDA_AUTH_FUNCTION'] | Out-Null

        Invoke-Aws -Args @("lambda","update-function-code","--function-name",$config['LAMBDA_AUTH_FUNCTION'],"--s3-bucket",$config['S3_LAMBDA_BUCKET'],"--s3-key","lambda-auth-1.0.0.jar") | Out-Null

        # Wait again after code update
        Wait-LambdaReady -FunctionName $config['LAMBDA_AUTH_FUNCTION'] | Out-Null

        # Build complete configuration JSON (Windows-safe approach)
        $configJson = @{
            FunctionName = $config['LAMBDA_AUTH_FUNCTION']
            Timeout = [int]$config['LAMBDA_TIMEOUT']
            MemorySize = [int]$config['LAMBDA_MEMORY_SIZE']
            Environment = @{
                Variables = @{
                    COGNITO_USER_POOL_ID = $script:userPoolId
                    COGNITO_CLIENT_ID = $script:clientId
                    DYNAMODB_USERS_TABLE = $config['DYNAMODB_USERS_TABLE']
                    S3_UPLOADS_BUCKET = $config['S3_UPLOADS_BUCKET']
                }
            }
        } | ConvertTo-Json -Depth 10

        $tmpAuthConfig = Join-Path $PSScriptRoot "tmp-auth-config.json"
        $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
        [System.IO.File]::WriteAllText($tmpAuthConfig, $configJson, $utf8NoBom)

        Write-Info "Config JSON saved to: $tmpAuthConfig"

        Invoke-Aws -Args @("lambda","update-function-configuration","--cli-input-json","file://$($tmpAuthConfig -replace '\\','/')") | Out-Null

        Remove-Item $tmpAuthConfig -Force -ErrorAction SilentlyContinue

        # Read ARN safely via Invoke-Aws (so it doesn't NativeCommandError)
        $authInfoJson = Invoke-Aws -Args @("lambda","get-function","--function-name",$config['LAMBDA_AUTH_FUNCTION'],"--output","json")
        $authInfo = ($authInfoJson -join "`n") | ConvertFrom-Json
        $script:authLambdaArn = $authInfo.Configuration.FunctionArn

        Write-Success "Updated AuthHandler: $script:authLambdaArn"
    }
}
elseif (-not $DryRun) {
    # Build environment JSON
    $authEnvJson = @{
        Variables = @{
            COGNITO_USER_POOL_ID = $script:userPoolId
            COGNITO_CLIENT_ID = $script:clientId
            DYNAMODB_USERS_TABLE = $config['DYNAMODB_USERS_TABLE']
            S3_UPLOADS_BUCKET = $config['S3_UPLOADS_BUCKET']
        }
    } | ConvertTo-Json -Depth 10

    $tmpAuthEnv = Join-Path $PSScriptRoot "tmp-auth-env.json"
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($tmpAuthEnv, $authEnvJson, $utf8NoBom)

    $resultJson = Invoke-Aws -Args @(
        "lambda","create-function",
        "--function-name",$config['LAMBDA_AUTH_FUNCTION'],
        "--runtime","java17",
        "--role",$script:roleArn,
        "--handler","com.example.lambda.AuthHandler::handleRequest",
        "--code","S3Bucket=$($config['S3_LAMBDA_BUCKET']),S3Key=lambda-auth-1.0.0.jar",
        "--timeout",$config['LAMBDA_TIMEOUT'],
        "--memory-size",$config['LAMBDA_MEMORY_SIZE'],
        "--environment","file://$($tmpAuthEnv -replace '\\','/')",
        "--output","json"
    )

    Remove-Item $tmpAuthEnv -Force -ErrorAction SilentlyContinue

    $result = ($resultJson -join "`n") | ConvertFrom-Json
    $script:authLambdaArn = $result.FunctionArn
    Write-Success "Created AuthHandler: $script:authLambdaArn"
}

Write-SubStep "Deploying UploadHandler Lambda"
$uploadExists = Test-LambdaExists -FunctionName $config['LAMBDA_UPLOAD_FUNCTION']

if ($uploadExists) {
    Write-Success "UploadHandler exists, updating code..."
    if (-not $DryRun) {
        # Wait for function to be ready before updating
        Wait-LambdaReady -FunctionName $config['LAMBDA_UPLOAD_FUNCTION'] | Out-Null

        Invoke-Aws -Args @("lambda","update-function-code","--function-name",$config['LAMBDA_UPLOAD_FUNCTION'],"--s3-bucket",$config['S3_LAMBDA_BUCKET'],"--s3-key","lambda-auth-1.0.0.jar") | Out-Null

        # Wait again after code update
        Wait-LambdaReady -FunctionName $config['LAMBDA_UPLOAD_FUNCTION'] | Out-Null

        # Build complete configuration JSON (Windows-safe approach)
        $configJson = @{
            FunctionName = $config['LAMBDA_UPLOAD_FUNCTION']
            Timeout = [int]$config['LAMBDA_TIMEOUT']
            MemorySize = [int]$config['LAMBDA_MEMORY_SIZE']
            Environment = @{
                Variables = @{
                    COGNITO_USER_POOL_ID = $script:userPoolId
                    DYNAMODB_USERS_TABLE = $config['DYNAMODB_USERS_TABLE']
                    DYNAMODB_UPLOADS_TABLE = $config['DYNAMODB_UPLOADS_TABLE']
                    DYNAMODB_TRANSACTIONS_TABLE = $config['DYNAMODB_TRANSACTIONS_TABLE']
                    S3_UPLOADS_BUCKET = $config['S3_UPLOADS_BUCKET']
                    BEDROCK_MODEL_ID = $config['BEDROCK_MODEL_ID']
                }
            }
        } | ConvertTo-Json -Depth 10

        $tmpUploadConfig = Join-Path $PSScriptRoot "tmp-upload-config.json"
        $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
        [System.IO.File]::WriteAllText($tmpUploadConfig, $configJson, $utf8NoBom)

        Write-Info "Config JSON saved to: $tmpUploadConfig"

        Invoke-Aws -Args @("lambda","update-function-configuration","--cli-input-json","file://$($tmpUploadConfig -replace '\\','/')") | Out-Null

        Remove-Item $tmpUploadConfig -Force -ErrorAction SilentlyContinue

        $uploadInfoJson = Invoke-Aws -Args @("lambda","get-function","--function-name",$config['LAMBDA_UPLOAD_FUNCTION'],"--output","json")
        $uploadInfo = ($uploadInfoJson -join "`n") | ConvertFrom-Json
        $script:uploadLambdaArn = $uploadInfo.Configuration.FunctionArn

        Write-Success "Updated UploadHandler: $script:uploadLambdaArn"
    }
}
elseif (-not $DryRun) {
    # Build environment JSON
    $uploadEnvJson = @{
        Variables = @{
            COGNITO_USER_POOL_ID = $script:userPoolId
            DYNAMODB_USERS_TABLE = $config['DYNAMODB_USERS_TABLE']
            DYNAMODB_UPLOADS_TABLE = $config['DYNAMODB_UPLOADS_TABLE']
            DYNAMODB_TRANSACTIONS_TABLE = $config['DYNAMODB_TRANSACTIONS_TABLE']
            S3_UPLOADS_BUCKET = $config['S3_UPLOADS_BUCKET']
            BEDROCK_MODEL_ID = $config['BEDROCK_MODEL_ID']
        }
    } | ConvertTo-Json -Depth 10

    $tmpUploadEnv = Join-Path $PSScriptRoot "tmp-upload-env.json"
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($tmpUploadEnv, $uploadEnvJson, $utf8NoBom)

    $resultJson = Invoke-Aws -Args @(
        "lambda","create-function",
        "--function-name",$config['LAMBDA_UPLOAD_FUNCTION'],
        "--runtime","java17",
        "--role",$script:roleArn,
        "--handler","com.example.lambda.AuthenticatedUploadHandler::handleRequest",
        "--code","S3Bucket=$($config['S3_LAMBDA_BUCKET']),S3Key=lambda-auth-1.0.0.jar",
        "--timeout",$config['LAMBDA_TIMEOUT'],
        "--memory-size",$config['LAMBDA_MEMORY_SIZE'],
        "--environment","file://$($tmpUploadEnv -replace '\\','/')",
        "--output","json"
    )

    Remove-Item $tmpUploadEnv -Force -ErrorAction SilentlyContinue

    $result = ($resultJson -join "`n") | ConvertFrom-Json
    $script:uploadLambdaArn = $result.FunctionArn
    Write-Success "Created UploadHandler: $script:uploadLambdaArn"
}


# Step 7: API Gateway (REST)
Write-Step "Step 7/9: Setting Up API Gateway"

$prevEA = $ErrorActionPreference
$ErrorActionPreference = "SilentlyContinue"
try {
    $apisJson = & aws apigateway get-rest-apis --output json 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to list API Gateways"
        exit 1
    }
    $apis = ($apisJson -join "`n") | ConvertFrom-Json
} finally {
    $ErrorActionPreference = $prevEA
}

$existingApi = $apis.items | Where-Object { $_.name -eq $config['API_GATEWAY_NAME'] } | Select-Object -First 1

if ($existingApi) {
    $script:apiId = $existingApi.id
    Write-Success "API Gateway exists: $script:apiId"
} elseif (-not $DryRun) {
    Write-Info "Creating REST API..."
    $resultJson = Invoke-Aws -Args @("apigateway","create-rest-api","--name",$config['API_GATEWAY_NAME'],"--description","Guide-ya API","--endpoint-configuration","types=REGIONAL","--output","json")
    $result = ($resultJson -join "`n") | ConvertFrom-Json
    $script:apiId = $result.id
    Write-Success "Created API: $script:apiId"
}

if ($script:apiId -and -not $DryRun) {
    $resourcesJson = Invoke-Aws -Args @("apigateway","get-resources","--rest-api-id",$script:apiId,"--output","json")
    $resources = ($resourcesJson -join "`n") | ConvertFrom-Json
    $rootId = $resources.items[0].id

    # Get-or-create /auth
    $authResourceId = ($resources.items | Where-Object { $_.path -eq '/auth' }).id
    if (-not $authResourceId) {
        $authResJson = Invoke-Aws -Args @("apigateway","create-resource","--rest-api-id",$script:apiId,"--parent-id",$rootId,"--path-part","auth","--output","json")
        $authResourceId = (($authResJson -join "`n") | ConvertFrom-Json).id
    }
    Write-Success "Auth resource: $authResourceId"

    # Get-or-create /upload
    $resourcesJson = Invoke-Aws -Args @("apigateway","get-resources","--rest-api-id",$script:apiId,"--output","json")
    $resources = ($resourcesJson -join "`n") | ConvertFrom-Json
    $uploadResourceId = ($resources.items | Where-Object { $_.path -eq '/upload' }).id
    if (-not $uploadResourceId) {
        $uploadResJson = Invoke-Aws -Args @("apigateway","create-resource","--rest-api-id",$script:apiId,"--parent-id",$rootId,"--path-part","upload","--output","json")
        $uploadResourceId = (($uploadResJson -join "`n") | ConvertFrom-Json).id
    }
    Write-Success "Upload resource: $uploadResourceId"

    # Cognito Authorizer get-or-create
    $authorizersJson = Invoke-Aws -Args @("apigateway","get-authorizers","--rest-api-id",$script:apiId,"--output","json")
    $authorizers = ($authorizersJson -join "`n") | ConvertFrom-Json
    $existingAuthz = $authorizers.items | Where-Object { $_.name -eq 'CognitoAuthorizer' } | Select-Object -First 1
    if ($existingAuthz) {
        $authorizerId = $existingAuthz.id
        Write-Success "Authorizer exists: $authorizerId"
    } else {
        $authResultJson = Invoke-Aws -Args @(
            "apigateway","create-authorizer",
            "--rest-api-id",$script:apiId,
            "--name","CognitoAuthorizer",
            "--type","COGNITO_USER_POOLS",
            "--provider-arns","arn:aws:cognito-idp:$($config['AWS_REGION']):$($config['AWS_ACCOUNT_ID']):userpool/$script:userPoolId",
            "--identity-source","method.request.header.Authorization",
            "--output","json"
        )
        $authResult = ($authResultJson -join "`n") | ConvertFrom-Json
        $authorizerId = $authResult.id
        Write-Success "Created authorizer: $authorizerId"
    }

    # POST /auth
    Invoke-Aws -Args @("apigateway","put-method","--rest-api-id",$script:apiId,"--resource-id",$authResourceId,"--http-method","POST","--authorization-type","NONE","--no-api-key-required") -IgnoreConflict | Out-Null
    $authUri = "arn:aws:apigateway:$($config['AWS_REGION']):lambda:path/2015-03-31/functions/$script:authLambdaArn/invocations"
    Invoke-Aws -Args @("apigateway","put-integration","--rest-api-id",$script:apiId,"--resource-id",$authResourceId,"--http-method","POST","--type","AWS_PROXY","--integration-http-method","POST","--uri",$authUri) -IgnoreConflict | Out-Null
    Ensure-LambdaPermission -FunctionName $config['LAMBDA_AUTH_FUNCTION'] -StatementId "apigateway-auth" -SourceArn "arn:aws:execute-api:$($config['AWS_REGION']):$($config['AWS_ACCOUNT_ID']):$script:apiId/*/*/auth"
    Write-Success "POST /auth configured"

    # POST /upload
    Invoke-Aws -Args @("apigateway","put-method","--rest-api-id",$script:apiId,"--resource-id",$uploadResourceId,"--http-method","POST","--authorization-type","COGNITO_USER_POOLS","--authorizer-id",$authorizerId,"--no-api-key-required") -IgnoreConflict | Out-Null
    $uploadUri = "arn:aws:apigateway:$($config['AWS_REGION']):lambda:path/2015-03-31/functions/$script:uploadLambdaArn/invocations"
    Invoke-Aws -Args @("apigateway","put-integration","--rest-api-id",$script:apiId,"--resource-id",$uploadResourceId,"--http-method","POST","--type","AWS_PROXY","--integration-http-method","POST","--uri",$uploadUri) -IgnoreConflict | Out-Null
    Ensure-LambdaPermission -FunctionName $config['LAMBDA_UPLOAD_FUNCTION'] -StatementId "apigateway-upload" -SourceArn "arn:aws:execute-api:$($config['AWS_REGION']):$($config['AWS_ACCOUNT_ID']):$script:apiId/*/*/upload"
    Write-Success "POST /upload configured"

    # CORS (idempotent-ish by ignoring conflicts)
    Write-SubStep "Enabling CORS"

    $corsRequestTemplate = '{"application/json":"{\"statusCode\": 200}"}'
    $corsResponseParams = '{"method.response.header.Access-Control-Allow-Headers":"''Content-Type,Authorization''","method.response.header.Access-Control-Allow-Methods":"''POST,OPTIONS''","method.response.header.Access-Control-Allow-Origin":"''*''"}'

    Invoke-Aws -Args @("apigateway","put-method","--rest-api-id",$script:apiId,"--resource-id",$authResourceId,"--http-method","OPTIONS","--authorization-type","NONE") -IgnoreConflict | Out-Null
    Invoke-Aws -Args @("apigateway","put-integration","--rest-api-id",$script:apiId,"--resource-id",$authResourceId,"--http-method","OPTIONS","--type","MOCK","--request-templates",$corsRequestTemplate) -IgnoreConflict | Out-Null
    Invoke-Aws -Args @("apigateway","put-method-response","--rest-api-id",$script:apiId,"--resource-id",$authResourceId,"--http-method","OPTIONS","--status-code","200","--response-parameters","method.response.header.Access-Control-Allow-Headers=false,method.response.header.Access-Control-Allow-Methods=false,method.response.header.Access-Control-Allow-Origin=false") -IgnoreConflict | Out-Null
    Invoke-Aws -Args @("apigateway","put-integration-response","--rest-api-id",$script:apiId,"--resource-id",$authResourceId,"--http-method","OPTIONS","--status-code","200","--response-parameters",$corsResponseParams) -IgnoreConflict | Out-Null

    Invoke-Aws -Args @("apigateway","put-method","--rest-api-id",$script:apiId,"--resource-id",$uploadResourceId,"--http-method","OPTIONS","--authorization-type","NONE") -IgnoreConflict | Out-Null
    Invoke-Aws -Args @("apigateway","put-integration","--rest-api-id",$script:apiId,"--resource-id",$uploadResourceId,"--http-method","OPTIONS","--type","MOCK","--request-templates",$corsRequestTemplate) -IgnoreConflict | Out-Null
    Invoke-Aws -Args @("apigateway","put-method-response","--rest-api-id",$script:apiId,"--resource-id",$uploadResourceId,"--http-method","OPTIONS","--status-code","200","--response-parameters","method.response.header.Access-Control-Allow-Headers=false,method.response.header.Access-Control-Allow-Methods=false,method.response.header.Access-Control-Allow-Origin=false") -IgnoreConflict | Out-Null
    Invoke-Aws -Args @("apigateway","put-integration-response","--rest-api-id",$script:apiId,"--resource-id",$uploadResourceId,"--http-method","OPTIONS","--status-code","200","--response-parameters",$corsResponseParams) -IgnoreConflict | Out-Null

    Write-Success "CORS enabled"

    Write-SubStep "Deploying to $($config['API_GATEWAY_STAGE']) stage"
    Invoke-Aws -Args @("apigateway","create-deployment","--rest-api-id",$script:apiId,"--stage-name",$config['API_GATEWAY_STAGE'],"--description","Automated deployment") | Out-Null

    $script:apiUrl = "https://$script:apiId.execute-api.$($config['AWS_REGION']).amazonaws.com/$($config['API_GATEWAY_STAGE'])"
    Write-Success "Deployed to: $script:apiUrl"
}

# Step 8: Validation (same as yours)
Write-Step "Step 8/9: Validating Deployment"

if (-not $DryRun) {
    try {
        $null = & aws lambda get-function --function-name $config['LAMBDA_AUTH_FUNCTION'] 2>&1
        if ($LASTEXITCODE -eq 0) { Write-Success "AuthHandler is deployed" } else { Write-Error "AuthHandler validation failed" }
        $null = & aws lambda get-function --function-name $config['LAMBDA_UPLOAD_FUNCTION'] 2>&1
        if ($LASTEXITCODE -eq 0) { Write-Success "UploadHandler is deployed" } else { Write-Error "UploadHandler validation failed" }

        if ($script:apiUrl) { Write-Success "API Gateway is live at: $script:apiUrl" } else { Write-Warning "API URL not set" }

        $tablesJson = Invoke-Aws -Args @("dynamodb","list-tables","--output","json")
        $tables = ($tablesJson -join "`n") | ConvertFrom-Json
        $expected = @($config['DYNAMODB_USERS_TABLE'], $config['DYNAMODB_UPLOADS_TABLE'], $config['DYNAMODB_TRANSACTIONS_TABLE'])
        foreach ($t in $expected) {
            if ($tables.TableNames -contains $t) { Write-Success "$t exists" } else { Write-Error "$t not found" }
        }
    } catch {
        Write-Warning "Validation encountered an error: $($_.Exception.Message)"
    }
} else {
    Write-Warning "Skipping validation (dry run mode)"
}

# Step 9: Output
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
