$ErrorActionPreference = 'Stop';
$ToolsPath             = Split-Path -Parent $MyInvocation.MyCommand.Definition;
$InstallDir            = Get-ToolsLocation; # Dangerous but appending -SpecificFolder to Install-ChocolateyZipPackage doesn't work

Install-ChocolateyZipPackage $env:ChocolateyPackageName "https://storage.googleapis.com/flutter_infra_release/releases/stable/windows/flutter_windows_3.38.5-stable.zip" $InstallDir -checksum '58e5042339a1a5363c741cac0c949a82145fa85681a4eb26ff08bc26b4051cea' -checksumType 'sha256';
Install-ChocolateyPath "$InstallDir\flutter\bin";