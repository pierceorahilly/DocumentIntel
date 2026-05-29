# Install Java 17 (Amazon Corretto)
# Amazon Corretto is a free, production-ready distribution of OpenJDK

Write-Host "Installing Amazon Corretto 17 (Java 17)..." -ForegroundColor Cyan

# Option 1: Using winget (Windows 10/11)
try {
    Write-Host "Attempting installation via winget..." -ForegroundColor Yellow
    winget install Amazon.Corretto.17 --accept-package-agreements --accept-source-agreements

    if ($LASTEXITCODE -eq 0) {
        Write-Host "[OK] Java 17 installed successfully!" -ForegroundColor Green
        Write-Host ""
        Write-Host "IMPORTANT: Close and reopen PowerShell for Java to work!" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "Then verify installation:" -ForegroundColor Cyan
        Write-Host "  java -version" -ForegroundColor White
        exit 0
    }
} catch {
    Write-Host "[WARN] winget installation failed, trying alternative..." -ForegroundColor Yellow
}

# Option 2: Download and install MSI
Write-Host ""
Write-Host "Downloading Java 17 installer..." -ForegroundColor Yellow

$downloadUrl = "https://corretto.aws/downloads/latest/amazon-corretto-17-x64-windows-jdk.msi"
$installerPath = "$env:TEMP\amazon-corretto-17.msi"

try {
    # Download installer
    Invoke-WebRequest -Uri $downloadUrl -OutFile $installerPath -UseBasicParsing

    Write-Host "[OK] Downloaded installer" -ForegroundColor Green
    Write-Host "Installing Java 17..." -ForegroundColor Yellow

    # Run installer silently
    Start-Process msiexec.exe -ArgumentList "/i `"$installerPath`" /quiet /norestart" -Wait

    # Clean up
    Remove-Item $installerPath -Force

    Write-Host "[OK] Java 17 installed successfully!" -ForegroundColor Green
    Write-Host ""
    Write-Host "IMPORTANT: Close and reopen PowerShell for Java to work!" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Then verify installation:" -ForegroundColor Cyan
    Write-Host "  java -version" -ForegroundColor White

} catch {
    Write-Host "[ERROR] Automatic installation failed" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please install manually:" -ForegroundColor Yellow
    Write-Host "1. Download: https://corretto.aws/downloads/latest/amazon-corretto-17-x64-windows-jdk.msi" -ForegroundColor White
    Write-Host "2. Double-click to install" -ForegroundColor White
    Write-Host "3. Close and reopen PowerShell" -ForegroundColor White
    Write-Host "4. Verify: java -version" -ForegroundColor White
    exit 1
}
