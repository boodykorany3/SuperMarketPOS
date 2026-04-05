param(
    [string]$AppVersion = "1.0.0",
    [string]$AppName = "SuperMarketPOS"
)

$ErrorActionPreference = "Stop"

function Resolve-MavenCommand {
    $mvn = Get-Command mvn -ErrorAction SilentlyContinue
    if ($mvn) {
        return $mvn.Source
    }

    $ideaMaven = "C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.2\plugins\maven\lib\maven3\bin\mvn.cmd"
    if (Test-Path $ideaMaven) {
        return $ideaMaven
    }

    throw "Maven command not found. Install Maven or use IntelliJ bundled Maven."
}

function Has-WixTools {
    $candle = Get-Command candle.exe -ErrorAction SilentlyContinue
    $light = Get-Command light.exe -ErrorAction SilentlyContinue
    return ($null -ne $candle) -and ($null -ne $light)
}

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $projectRoot

$mvnCommand = Resolve-MavenCommand
Write-Host "Using Maven: $mvnCommand"
& $mvnCommand -DskipTests clean package

$desktopJar = Join-Path $projectRoot "target\SuperMarketPOS-1.0-desktop.jar"
if (-not (Test-Path $desktopJar)) {
    throw "Desktop fat JAR not found at: $desktopJar"
}

$installerDir = Join-Path $projectRoot "target\installer"
$inputDir = Join-Path $installerDir "input"

if (Test-Path $inputDir) {
    Remove-Item -Recurse -Force $inputDir
}

New-Item -ItemType Directory -Force -Path $inputDir | Out-Null
Copy-Item -Force $desktopJar $inputDir

$commonJpackageArgs = @(
    "--name", $AppName,
    "--app-version", $AppVersion,
    "--vendor", "SuperMarketPOS",
    "--input", $inputDir,
    "--main-jar", "SuperMarketPOS-1.0-desktop.jar",
    "--dest", $installerDir
)

if (Has-WixTools) {
    Write-Host "WiX detected: building .exe installer"
    & jpackage --type exe @commonJpackageArgs --win-dir-chooser --win-menu --win-shortcut --win-per-user-install
    Write-Host "Installer built under: $installerDir"
    exit 0
}

Write-Warning "WiX (candle.exe + light.exe) not found. Building portable app-image instead."
& jpackage --type app-image @commonJpackageArgs

$portableDir = Join-Path $installerDir $AppName
$portableZip = Join-Path $installerDir "$AppName-portable.zip"
if (Test-Path $portableZip) {
    Remove-Item -Force $portableZip
}
Compress-Archive -Path (Join-Path $portableDir "*") -DestinationPath $portableZip -Force

Write-Host "Portable package built:"
Write-Host "Folder: $portableDir"
Write-Host "Zip:    $portableZip"
