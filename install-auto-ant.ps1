<#
.SYNOPSIS
Builds auto-ant and installs it as a user-level CLI command on Windows.

.DESCRIPTION
The Gradle application plugin creates launch scripts under app/build/install.
This installer copies that distribution to a stable per-user install directory
and adds the distribution's bin directory to the user's PATH so `auto-ant` can
be run from any new terminal.
#>
[CmdletBinding()]
param(
    [string] $InstallDir = (Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'Programs\auto-ant'),
    [switch] $SkipBuild,
    [switch] $NoPathUpdate
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Write-Info {
    param([Parameter(Mandatory = $true)][string] $Message)
    Write-Host "auto-ant: $Message"
}

function Get-NormalizedPath {
    param([Parameter(Mandatory = $true)][string] $Path)

    $expandedPath = [Environment]::ExpandEnvironmentVariables($Path)
    $trimCharacters = [char[]] @('\', '/')

    try {
        return [System.IO.Path]::GetFullPath($expandedPath).TrimEnd($trimCharacters)
    } catch {
        return $expandedPath.TrimEnd($trimCharacters)
    }
}

function Add-UserPathEntry {
    param([Parameter(Mandatory = $true)][string] $PathEntry)

    $normalizedPathEntry = Get-NormalizedPath $PathEntry
    $userPath = [Environment]::GetEnvironmentVariable('Path', 'User')

    $entries = @()
    if (-not [string]::IsNullOrWhiteSpace($userPath)) {
        $entries = $userPath -split ';' | ForEach-Object { $_.Trim() } | Where-Object { $_ }
    }

    foreach ($entry in $entries) {
        if ((Get-NormalizedPath $entry) -ieq $normalizedPathEntry) {
            Write-Info "User PATH already contains $PathEntry."
            return $false
        }
    }

    [Environment]::SetEnvironmentVariable('Path', (($entries + $PathEntry) -join ';'), 'User')

    $currentPathContainsEntry = $false
    if (-not [string]::IsNullOrWhiteSpace($env:Path)) {
        foreach ($entry in ($env:Path -split ';' | ForEach-Object { $_.Trim() } | Where-Object { $_ })) {
            if ((Get-NormalizedPath $entry) -ieq $normalizedPathEntry) {
                $currentPathContainsEntry = $true
                break
            }
        }
    }

    if (-not $currentPathContainsEntry) {
        if ([string]::IsNullOrWhiteSpace($env:Path)) {
            $env:Path = $PathEntry
        } else {
            $env:Path = "$($env:Path.TrimEnd(';'));$PathEntry"
        }
    }

    Write-Info "Added $PathEntry to your user PATH."
    return $true
}

$repoRoot = $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($repoRoot)) {
    $repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
}

$InstallDir = Get-NormalizedPath $InstallDir
$gradleWrapper = Join-Path $repoRoot 'gradlew.bat'
$distributionDir = Join-Path $repoRoot 'app\build\install\auto-ant'

Push-Location $repoRoot
try {
    if (-not (Test-Path $gradleWrapper -PathType Leaf)) {
        throw "Could not find Gradle wrapper at $gradleWrapper. Run this script from the auto-ant repository."
    }

    if (-not $SkipBuild) {
        Write-Info 'Building the CLI distribution with Gradle...'
        & $gradleWrapper --no-daemon installDist
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle installDist failed with exit code $LASTEXITCODE."
        }
    }

    if (-not (Test-Path $distributionDir -PathType Container)) {
        throw "Could not find built distribution at $distributionDir. Run without -SkipBuild to build it first."
    }

    Write-Info "Installing distribution to $InstallDir..."
    if (Test-Path $InstallDir) {
        Remove-Item $InstallDir -Recurse -Force
    }

    $installParent = Split-Path -Parent $InstallDir
    New-Item -ItemType Directory -Path $installParent -Force | Out-Null
    New-Item -ItemType Directory -Path $InstallDir -Force | Out-Null
    Copy-Item -Path (Join-Path $distributionDir '*') -Destination $InstallDir -Recurse -Force

    $binDir = Join-Path $InstallDir 'bin'
    $launcher = Join-Path $binDir 'auto-ant.bat'

    if (-not (Test-Path $launcher -PathType Leaf)) {
        throw "The installed launcher was not found at $launcher."
    }

    if (-not $NoPathUpdate) {
        [void] (Add-UserPathEntry $binDir)
    }

    Write-Host ''
    Write-Host "Installed auto-ant to: $InstallDir"
    Write-Host "Launcher: $launcher"

    if ($NoPathUpdate) {
        Write-Host "PATH was not changed. Add this directory to PATH to run auto-ant from anywhere: $binDir"
    } else {
        Write-Host 'Open a new terminal, then run: auto-ant --help'
    }
} finally {
    Pop-Location
}