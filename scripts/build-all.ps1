<#
.SYNOPSIS
    Baut und testet Ardania Info HUD fuer alle Minecraft-Targets aus build-targets.json.

.DESCRIPTION
    Die Target-spezifischen Werte werden per -P an Gradle uebergeben, gradle.properties
    wird dabei nicht veraendert. Die fertigen JARs landen in build/distributions/<target-id>/.

.EXAMPLE
    .\scripts\build-all.ps1
    .\scripts\build-all.ps1 -Targets mc26.x
    .\scripts\build-all.ps1 -SkipTests -JavaHome "C:\Users\Sven\.jdks\temurin-25.0.4.1"
#>
param(
    # Nur diese Target-IDs bauen (z. B. mc1.21.x, mc26.x). Standard: alle.
    [string[]]$Targets,
    # Unit-Tests ueberspringen.
    [switch]$SkipTests,
    # JDK fuer Gradle (Standard: vorhandenes JAVA_HOME). Benoetigt Java 25 oder neuer.
    [string]$JavaHome
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if ($JavaHome) {
    $env:JAVA_HOME = $JavaHome
}
if (-not $env:JAVA_HOME) {
    throw "JAVA_HOME ist nicht gesetzt. Bitte -JavaHome angeben (Java 25 oder neuer)."
}

$allTargets = Get-Content (Join-Path $root 'build-targets.json') -Raw | ConvertFrom-Json
if ($Targets) {
    $unknown = $Targets | Where-Object { $_ -notin $allTargets.id }
    if ($unknown) {
        throw "Unbekannte Target-ID(s): $($unknown -join ', '). Verfuegbar: $($allTargets.id -join ', ')"
    }
    $selected = $allTargets | Where-Object { $_.id -in $Targets }
} else {
    $selected = $allTargets
}

$versionLine = Select-String -Path (Join-Path $root 'gradle.properties') -Pattern '^\s*version\s*=\s*(.+)$' | Select-Object -First 1
if (-not $versionLine) {
    throw "Konnte 'version' nicht in gradle.properties finden."
}
$modVersion = $versionLine.Matches[0].Groups[1].Value.Trim()

$tasks = if ($SkipTests) { @('build', '-x', 'test') } else { @('test', 'build') }
$results = @()

foreach ($t in $selected) {
    $baseName = "ardania-info-hud-$($t.id)"
    Write-Host ""
    Write-Host "=== $($t.id) (MC $($t.minecraft_version)) -> v$modVersion ===" -ForegroundColor Cyan

    $gradleArgs = @(
        "-Pminecraft_version=$($t.minecraft_version)",
        "-Pfabric_api_version=$($t.fabric_api_version)",
        "-Pmodmenu_version=$($t.modmenu_version)",
        "-Parchives_base_name=$baseName",
        '--no-daemon'
    ) + $tasks

    & (Join-Path $root 'gradlew.bat') @gradleArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Build fuer $($t.id) fehlgeschlagen (Exit-Code $LASTEXITCODE)."
    }

    $jars = Get-ChildItem -Path (Join-Path $root 'build\libs') -Filter "$baseName-$modVersion*.jar"
    if (-not $jars) {
        throw "Keine JAR fuer $($t.id) in build\libs gefunden."
    }

    $distDir = Join-Path $root "build\distributions\$($t.id)"
    New-Item -ItemType Directory -Force -Path $distDir | Out-Null
    # Alte Versionen entfernen, damit pro Target nur der aktuelle Stand liegt
    Get-ChildItem -Path $distDir -Filter '*.jar' | Remove-Item -Force
    $jars | Copy-Item -Destination $distDir -Force

    $results += [pscustomobject]@{
        Target = $t.id
        Minecraft = $t.minecraft_version
        Jar = (Join-Path "build\distributions\$($t.id)" "$baseName-$modVersion.jar")
    }
}

Write-Host ""
Write-Host "Alle Builds erfolgreich (v$modVersion):" -ForegroundColor Green
$results | Format-Table -AutoSize
