# Guide-ya AWS Deployment Script (PowerShell)
# Automated deployment of all AWS resources

param(
    [switch]$SkipBuild,
    [switch]$DryRun,
    [switch]$Verbose
)

$ErrorActionPreference = "Stop"

# Colors for output
function Write-Success { Write-Host $args -ForegroundColor Green }
function Write-Info { Write-Host $args -ForegroundColor Cyan }
function Write-Warning { Write-Host $args -ForegroundColor Yellow }
function Write-Error { Write-Host $args -ForegroundColor Red }

Write-Info "========================================="
Write-Info "  Guide-ya AWS Deployment Script"
Write-Info "========================================="
Write-Info ""

# Load configuration
$configFile = Join-Path $PSScriptRoot "config.properties"
if (-not (Test-Path $configFile)) {
    Write-Error "Configuration file not found: $configFile"
    Write-Error "Please create config.properties from the template"
    exit 1
}

Write-Info "Loading configuration from $configFile..."
$config = @{}
Get-Content $configFile | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]+?)\s*=\s*(.+?)\s*$') {
        $config[$matches[1]] = $matches[2]
    }
}

# Validate required fields
$requiredFields = @(
    'AWS_REGION', 'AWS_ACCOUNT_ID', 'S3_UPLOADS_BUCKET', 'S3_LAMBDA_BUCKET'
)

$missingFields = @()
foreach ($field in $requiredFields) {
    if (-not $config[$field] -or $config[$field] -match 'YOUR_.*_HERE') {
        $missingFields += $field
    }
}

if ($missingFields.Count -gt 0) {
    Write-Error "Please update config.properties with your values:"
    $missingFields | ForEach-Object { Write-Error "  - $_" }
    exit 1
}

Write-Success "Configuration loaded successfully"

# Check prerequisites
Write-Info ""
Write-Info "Checking prerequisites..."

# Check AWS CLI
try {
    $awsVersion = aws --version 2>&1
    Write-Success "✓ AWS CLI installed: $awsVersion"
} catch {
    Write-Error "✗ AWS CLI not found. Install from: https://aws.amazon.com/cli/"
    exit 1
}

# Check AWS credentials
try {
    $caller = aws sts get-caller-identity --output json | ConvertFrom-Json
    Write-Success "✓ AWS credentials configured"
    Write-Info "  Account: $($caller.Account)"
    Write-Info "  User: $($caller.Arn)"
} catch {
    Write-Error "✗ AWS credentials not configured. Run: aws configure"
    exit 1
}

# Check Java
try {
    $javaVersion = java -version 2>&1 | Select-Object -First 1
    Write-Success "✓ Java installed: $javaVersion"
} catch {
    Write-Error "✗ Java not found. Install Java 17 or higher"
    exit 1
}

# Check Maven
try {
    $mvnVersion = mvn -version | Select-Object -First 1
    Write-Success "✓ Maven installed: $mvnVersion"
} catch {
    Write-Error "✗ Maven not found. Install from: https://maven.apache.org/download.cgi"
    exit 1
}

if ($DryRun) {
    Write-Warning "DRY RUN MODE - No changes will be made"
    Write-Info ""
}

# Step 1: Build Lambda JAR
Write-Info ""
Write-Info "========================================="
Write-Info "Step 1: Building Lambda JAR"
Write-Info "========================================="

$projectRoot = Split-Path -Parent $PSScriptRoot
$jarFile = Join-Path $projectRoot "target\lambda-auth-1.0.0.jar"

if ($SkipBuild -and (Test-Path $jarFile)) {
    Write-Warning "Skipping build (using existing JAR)"
} else {
    Write-Info "Running: mvn clean package"
    Push-Location $projectRoot
    try {
        mvn clean package -DskipTests
        if ($LASTEXITCODE -ne 0) {
            Write-Error "Maven build failed"
            exit 1
        }
        Write-Success "✓ Lambda JAR built successfully"
    } finally {
        Pop-Location
    }
}

if (-not (Test-Path $jarFile)) {
    Write-Error "JAR file not found: $jarFile"
    exit 1
}

$jarSize = (Get-Item $jarFile).Length / 1MB
Write-Info "JAR size: $($jarSize.ToString('F2')) MB"

# Step 2: Create S3 Buckets
Write-Info ""
Write-Info "========================================="
Write-Info "Step 2: Creating S3 Buckets"
Write-Info "========================================="

function Create-S3Bucket {
    param($bucketName, $region)

    $exists = aws s3 ls "s3://$bucketName" 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Warning "✓ Bucket already exists: $bucketName"
        return $true
    }

    Write-Info "Creating bucket: $bucketName"
    if (-not $DryRun) {
        if ($region -eq "us-east-1") {
            aws s3 mb "s3://$bucketName" --region $region
        } else {
            aws s3api create-bucket --bucket $bucketName --region $region --create-bucket-configuration LocationConstraint=$region
        }

        if ($LASTEXITCODE -ne 0) {
            Write-Error "Failed to create bucket: $bucketName"
            return $false
        }

        # Block public access
        aws s3api put-public-access-block --bucket $bucketName --public-access-block-configuration "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"
    }

    Write-Success "✓ Created bucket: $bucketName"
    return $true
}

Create-S3Bucket $config['S3_LAMBDA_BUCKET'] $config['AWS_REGION']
Create-S3Bucket $config['S3_UPLOADS_BUCKET'] $config['AWS_REGION']

# Upload Lambda JAR
Write-Info "Uploading Lambda JAR to S3..."
if (-not $DryRun) {
    aws s3 cp $jarFile "s3://$($config['S3_LAMBDA_BUCKET'])/lambda-auth-1.0.0.jar"
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to upload JAR to S3"
        exit 1
    }
}
Write-Success "✓ Uploaded Lambda JAR"

# Step 3: Create Cognito User Pool
Write-Info ""
Write-Info "========================================="
Write-Info "Step 3: Creating Cognito User Pool"
Write-Info "========================================="

$userPoolId = $null
$userPools = aws cognito-idp list-user-pools --max-results 60 --output json | ConvertFrom-Json

foreach ($pool in $userPools.UserPools) {
    if ($pool.Name -eq $config['COGNITO_USER_POOL_NAME']) {
        $userPoolId = $pool.Id
        Write-Warning "✓ User Pool already exists: $userPoolId"
        break
    }
}

if (-not $userPoolId -and -not $DryRun) {
    Write-Info "Creating Cognito User Pool..."

    $poolConfig = @{
        PoolName = $config['COGNITO_USER_POOL_NAME']
        Policies = @{
            PasswordPolicy = @{
                MinimumLength = 8
                RequireUppercase = $true
                RequireLowercase = $true
                RequireNumbers = $true
                RequireSymbols = $true
            }
        }
        AutoVerifiedAttributes = @("email")
        UsernameAttributes = @("email")
        Schema = @(
            @{
                Name = "email"
                Required = $true
                Mutable = $false
            },
            @{
                Name = "name"
                Required = $true
                Mutable = $true
            },
            @{
                Name = "birthdate"
                Required = $false
                Mutable = $true
            },
            @{
                Name = "address"
                Required = $false
                Mutable = $true
            }
        )
    } | ConvertTo-Json -Depth 10

    $result = aws cognito-idp create-user-pool --pool-name $config['COGNITO_USER_POOL_NAME'] --cli-input-json $poolConfig --output json | ConvertFrom-Json
    $userPoolId = $result.UserPool.Id
    Write-Success "✓ Created User Pool: $userPoolId"
}

# Create App Client
$clientId = $null
if ($userPoolId -and -not $DryRun) {
    $clients = aws cognito-idp list-user-pool-clients --user-pool-id $userPoolId --output json | ConvertFrom-Json

    foreach ($client in $clients.UserPoolClients) {
        if ($client.ClientName -eq $config['COGNITO_APP_CLIENT_NAME']) {
            $clientId = $client.ClientId
            Write-Warning "✓ App Client already exists: $clientId"
            break
        }
    }

    if (-not $clientId) {
        Write-Info "Creating App Client..."
        $result = aws cognito-idp create-user-pool-client `
            --user-pool-id $userPoolId `
            --client-name $config['COGNITO_APP_CLIENT_NAME'] `
            --no-generate-secret `
            --explicit-auth-flows ALLOW_USER_PASSWORD_AUTH ALLOW_REFRESH_TOKEN_AUTH ALLOW_USER_SRP_AUTH `
            --output json | ConvertFrom-Json

        $clientId = $result.UserPoolClient.ClientId
        Write-Success "✓ Created App Client: $clientId"
    }
}

# Save Cognito IDs
if ($userPoolId -and $clientId) {
    Write-Info ""
    Write-Info "Cognito Configuration:"
    Write-Info "  User Pool ID: $userPoolId"
    Write-Info "  Client ID: $clientId"

    # Save to file for later use
    @{
        UserPoolId = $userPoolId
        ClientId = $clientId
    } | ConvertTo-Json | Out-File -FilePath (Join-Path $PSScriptRoot "cognito-config.json")
}

Write-Info ""
Write-Info "========================================="
Write-Info "Deployment script created!"
Write-Info "========================================="
Write-Info ""
Write-Warning "IMPORTANT: This is a partial deployment script."
Write-Warning "Complete deployment requires:"
Write-Warning "  1. DynamoDB table creation"
Write-Warning "  2. IAM role creation"
Write-Warning "  3. Lambda function deployment"
Write-Warning "  4. API Gateway setup"
Write-Info ""
Write-Info "Would you like me to continue with the full deployment?"
Write-Info "This will take approximately 30-45 minutes."
Write-Info ""
Write-Info "Next steps:"
Write-Info "  1. Review config.properties"
Write-Info "  2. Update AWS_ACCOUNT_ID and bucket names"
Write-Info "  3. Run: .\deploy.ps1"
