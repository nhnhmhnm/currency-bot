param([string]$ConfigPath = (Join-Path $PSScriptRoot "../src/main/resources/application-secret.yml"))

$ErrorActionPreference = "Stop"
$path = (Resolve-Path -LiteralPath $ConfigPath).Path
$content = [System.IO.File]::ReadAllText($path)
$matches = [regex]::Matches($content, '(?m)^([ \t]*secret:[ \t]*)([^\r\n#]+)')
if ($matches.Count -ne 1) { throw "Expected exactly one JWT secret in $path" }

$bytes = New-Object byte[] 64
$random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $random.GetBytes($bytes) } finally { $random.Dispose() }
$newSecret = [Convert]::ToBase64String($bytes)
$match = $matches[0].Groups[2]
$updated = $content.Substring(0, $match.Index) + $newSecret + $content.Substring($match.Index + $match.Length)
[System.IO.File]::WriteAllText($path, $updated, (New-Object System.Text.UTF8Encoding($false)))
Write-Output "Rotated local JWT secret in $path"
