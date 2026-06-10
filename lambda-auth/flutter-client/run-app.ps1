# Smart Flutter run script - auto-detects git branch and uses correct environment
# Usage: .\run-app.ps1 [--release]

param(
    [switch]$release = $false
)

# Get current git branch
$currentBranch = (git rev-parse --abbrev-ref HEAD).Trim()

Write-Host "Current branch: $currentBranch" -ForegroundColor Cyan

# Determine API URL based on branch
$apiUrl = switch ($currentBranch) {
    "staging" {
        Write-Host "🟠 Using STAGING environment" -ForegroundColor Yellow
        "https://vz1307i8t2.execute-api.eu-west-1.amazonaws.com/staging"
    }
    "main" {
        Write-Host "🟢 Using PRODUCTION environment" -ForegroundColor Green
        "https://vz1307i8t2.execute-api.eu-west-1.amazonaws.com/prod"
    }
    default {
        Write-Host "⚠️ Unknown branch '$currentBranch', defaulting to PRODUCTION" -ForegroundColor Yellow
        "https://vz1307i8t2.execute-api.eu-west-1.amazonaws.com/prod"
    }
}

Write-Host "API Base URL: $apiUrl" -ForegroundColor Cyan
Write-Host ""

# Build flutter command
$flutterCmd = "flutter run --dart-define=API_BASE_URL=$apiUrl"

if ($release) {
    $flutterCmd += " --release"
}

Write-Host "Running: $flutterCmd" -ForegroundColor Gray
Write-Host ""

# Execute flutter run
Invoke-Expression $flutterCmd
