# MCJEBooster Build Script with CNR
# Version: 26.7-20260726
# Purpose: Automated build using CommandnRead (CNR) tool

param(
    [switch]$Clean,
    [switch]$Test,
    [switch]$Package,
    [switch]$Release,
    [string]$Version = "26.7-20260726"
)

$ErrorActionPreference = "Stop"
$CNR_PATH = "c:\Users\Sails\Documents\Workspace\cnr-v26.0-alpha.1-windows-x86_64\cnr.exe"
$PROJECT_ROOT = "c:\Users\Sails\Documents\Workspace\NormalWorkspace\Minecraft-Projects\MCJEBooster"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "MCJEBooster Build Script v26.7" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check if CNR is available
if (-not (Test-Path $CNR_PATH)) {
    Write-Host "ERROR: CNR tool not found at: $CNR_PATH" -ForegroundColor Red
    Write-Host "Please ensure CNR is installed and path is correct." -ForegroundColor Yellow
    exit 1
}

# Register CNR agent instance for this build
Write-Host "[1/6] Registering CNR agent instance..." -ForegroundColor Green
& $CNR_PATH register -a mcjebooster_build -n build_$(Get-Date -Format 'yyyyMMdd_HHmmss') -w $PROJECT_ROOT
if ($LASTEXITCODE -ne 0) {
    Write-Host "Warning: Could not register CNR instance (may already exist)" -ForegroundColor Yellow
}

# Function to execute command via CNR
function Invoke-CNRCommand {
    param(
        [string]$Command,
        [string]$Description
    )
    
    Write-Host $Description -ForegroundColor Cyan
    & $CNR_PATH exec -a mcjebooster_build -i build_latest -c $Command
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: Command failed with exit code $LASTEXITCODE" -ForegroundColor Red
        exit $LASTEXITCODE
    }
}

# Clean build
if ($Clean) {
    Write-Host "[2/6] Cleaning previous build..." -ForegroundColor Green
    Invoke-CNRCommand -Command "mvn clean" -Description "  -> Running Maven clean..."
} else {
    Write-Host "[2/6] Skipping clean (use -Clean to enable)" -ForegroundColor Yellow
}

# Compile
Write-Host "[3/6] Compiling source code..." -ForegroundColor Green
Invoke-CNRCommand -Command "mvn compile -DskipTests" -Description "  -> Running Maven compile..."

# Run tests
if ($Test) {
    Write-Host "[4/6] Running tests..." -ForegroundColor Green
    Invoke-CNRCommand -Command "mvn test" -Description "  -> Running Maven test..."
} else {
    Write-Host "[4/6] Skipping tests (use -Test to enable)" -ForegroundColor Yellow
}

# Package
if ($Package -or $Release) {
    Write-Host "[5/6] Packaging JAR..." -ForegroundColor Green
    Invoke-CNRCommand -Command "mvn package -DskipTests" -Description "  -> Running Maven package..."
    
    $jarFile = Join-Path $PROJECT_ROOT "target\MCJEBooster-$Version.jar"
    if (Test-Path $jarFile) {
        Write-Host "  -> JAR created: $jarFile" -ForegroundColor Green
        
        # Display JAR size
        $size = (Get-Item $jarFile).Length / 1MB
        Write-Host "  -> Size: $([math]::Round($size, 2)) MB" -ForegroundColor Cyan
    } else {
        Write-Host "  -> ERROR: JAR not found" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "[5/6] Skipping package (use -Package to enable)" -ForegroundColor Yellow
}

# Create release bundle
if ($Release) {
    Write-Host "[6/6] Creating release bundle..." -ForegroundColor Green
    
    $releaseDir = Join-Path $PROJECT_ROOT "releases\$Version"
    New-Item -ItemType Directory -Force -Path $releaseDir | Out-Null
    
    # Copy JAR
    Copy-Item (Join-Path $PROJECT_ROOT "target\MCJEBooster-$Version.jar") $releaseDir
    
    # Copy adapters
    Copy-Item (Join-Path $PROJECT_ROOT "adapters\*") $releaseDir -Recurse
    
    # Copy documentation
    Copy-Item (Join-Path $PROJECT_ROOT "README.md") $releaseDir
    Copy-Item (Join-Path $PROJECT_ROOT "LICENSE") $releaseDir
    Copy-Item (Join-Path $PROJECT_ROOT "CHANGELOG.md") $releaseDir
    Copy-Item (Join-Path $PROJECT_ROOT "config\mcjebooster.yml") (Join-Path $releaseDir "config.yml.example")
    
    # Copy docs
    $docsDir = Join-Path $releaseDir "docs"
    New-Item -ItemType Directory -Force -Path $docsDir | Out-Null
    Copy-Item (Join-Path $PROJECT_ROOT "docs\*") $docsDir -Recurse
    
    # Generate checksums
    Write-Host "  -> Generating checksums..." -ForegroundColor Cyan
    $jarPath = Join-Path $releaseDir "MCJEBooster-$Version.jar"
    $hash = Get-FileHash -Path $jarPath -Algorithm SHA256
    $checksumFile = Join-Path $releaseDir "checksums.sha256"
    "$($hash.Hash)  MCJEBooster-$Version.jar" | Out-File -FilePath $checksumFile -Encoding UTF8
    
    # Create ZIP archive
    Write-Host "  -> Creating ZIP archive..." -ForegroundColor Cyan
    $zipFile = Join-Path $PROJECT_ROOT "releases\MCJEBooster-v$Version-Full.zip"
    Compress-Archive -Path "$releaseDir\*" -DestinationPath $zipFile -Force
    
    Write-Host "  -> Release bundle created: $zipFile" -ForegroundColor Green
    
    $zipSize = (Get-Item $zipFile).Length / 1MB
    Write-Host "  -> Archive size: $([math]::Round($zipSize, 2)) MB" -ForegroundColor Cyan
} else {
    Write-Host "[6/6] Skipping release bundle (use -Release to enable)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Build completed successfully!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "  1. Review build artifacts in target/" -ForegroundColor White
Write-Host "  2. Test the JAR: java -jar target\MCJEBooster-$Version.jar" -ForegroundColor White
if ($Release) {
    Write-Host "  3. Publish release from: releases\$Version" -ForegroundColor White
}
Write-Host ""
