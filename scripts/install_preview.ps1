param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$Apk = (Join-Path $PSScriptRoot '..\app\build\outputs\apk\release\app-release.apk')
)
$ErrorActionPreference = 'Stop'
if (!(Test-Path -LiteralPath $Apk -PathType Leaf)) { throw "APK not found: $Apk" }
# A serial is mandatory so this script never chooses an unrelated connected device.
& adb -s $Serial install -r $Apk
if ($LASTEXITCODE -ne 0) { throw 'APK installation failed' }
& adb -s $Serial shell am start -n com.mooncast.host/.MainActivity
if ($LASTEXITCODE -ne 0) { throw 'Activity launch failed' }
Write-Output 'Open MoonCast on the phone, allow screen sharing, then connect with Moonlight.'
