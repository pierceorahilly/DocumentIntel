# CONFIG
$avdName = "Pixel_7_API_36"
$emulatorPath = "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe"

Write-Host "Killing any running emulators..."
& adb devices | Select-String "emulator" | ForEach-Object {
    $id = ($_ -split "`t")[0]
    Write-Host "Killing $id"
    adb -s $id emu kill
}

Start-Sleep -Seconds 2

Write-Host "Starting emulator: $avdName..."
Start-Process $emulatorPath -ArgumentList "-avd $avdName"

Write-Host "Emulator started. Wait for it to fully boot before running Flutter."
