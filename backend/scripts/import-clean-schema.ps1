param([string]$Database = "cuunit")

$ErrorActionPreference = "Stop"
if ($Database -notmatch '^[a-z][a-z0-9_]{2,63}$') {
    throw "Database name must contain only lowercase letters, digits, and underscores"
}

$container = docker inspect mysql | ConvertFrom-Json | Select-Object -First 1
if ($LASTEXITCODE -ne 0 -or $null -eq $container) { throw "MySQL container named 'mysql' is required" }
$passwordEntry = $container.Config.Env | Where-Object { $_ -like "MYSQL_ROOT_PASSWORD=*" } | Select-Object -First 1
if (-not $passwordEntry) { throw "The MySQL container has no MYSQL_ROOT_PASSWORD" }
$rootPassword = $passwordEntry.Substring("MYSQL_ROOT_PASSWORD=".Length)

try {
    $exists = docker exec -e "MYSQL_PWD=$rootPassword" mysql mysql -uroot -N -e "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$Database'"
    if ($LASTEXITCODE -ne 0 -or $exists -ne "1") { throw "Target database must already exist: $Database" }
    $tableCount = docker exec -e "MYSQL_PWD=$rootPassword" mysql mysql -uroot -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$Database'"
    if ($LASTEXITCODE -ne 0 -or $tableCount -ne "0") { throw "Target database must have no tables: $Database" }

    $baseline = Join-Path $PSScriptRoot "migrations/clean-baseline-mysql94.sql"
    $seed = Join-Path $PSScriptRoot "migrations/clean-reference-seed.sql"
    Get-Content -LiteralPath $baseline -Raw -Encoding utf8 | docker exec -i -e "MYSQL_PWD=$rootPassword" mysql mysql -uroot $Database
    if ($LASTEXITCODE -ne 0) { throw "Baseline import failed; inspect the partially populated target before retrying" }
    Get-Content -LiteralPath $seed -Raw -Encoding utf8 | docker exec -i -e "MYSQL_PWD=$rootPassword" mysql mysql -uroot $Database
    if ($LASTEXITCODE -ne 0) { throw "Reference seed import failed; inspect the partially populated target before retrying" }

    $result = @(docker exec -e "MYSQL_PWD=$rootPassword" mysql mysql -uroot -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$Database'; SELECT GROUP_CONCAT(CONCAT(id, ':', code) ORDER BY id) FROM $Database.currency; SELECT COUNT(*) FROM $Database.bank; SELECT COUNT(*) FROM $Database.internal_account")
    if ($LASTEXITCODE -ne 0 -or $result.Count -ne 4 -or $result[0] -ne "19" -or
        $result[1] -ne "1:KRW,2:USD,3:JPY" -or $result[2] -ne "7" -or $result[3] -ne "4") {
        throw "Post-import schema or reference data does not match the expected baseline: $($result -join ', ')"
    }
    Write-Output "Imported clean schema into ${Database}: 19 tables, 3 currencies, 7 banks, 4 zero-balance company accounts"
} finally {
    $rootPassword = $null
}
