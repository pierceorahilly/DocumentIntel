# CONFIG
$projectPath = "D:\SIMPLE-BANK-AI_PATCH\lambda-auth\flutter-client"

# Navigate to Flutter project root
Set-Location $projectPath

# Wait until at least one Android device is detected
Write-Host "Waiting for Android device..."
do {
    Start-Sleep -Seconds 5
    $deviceLine = (& flutter devices | Select-String "• android" | Select-Object -First 1).Line
} while (-not $deviceLine)

$firstDevice = $deviceLine.Split("•")[0].Trim()
Write-Host "Detected device: $firstDevice"

# Clean and fetch packages
flutter clean
flutter pub get

# Launch Flutter app
flutter run -d $firstDevice
