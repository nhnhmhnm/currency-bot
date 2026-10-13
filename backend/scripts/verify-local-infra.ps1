$ErrorActionPreference = "Stop"
$database = "currency_bot_integration_test"

$container = docker inspect mysql | ConvertFrom-Json | Select-Object -First 1
if ($LASTEXITCODE -ne 0 -or $null -eq $container) {
    throw "MySQL container named 'mysql' is required"
}
$passwordEntry = $container.Config.Env | Where-Object { $_ -like "MYSQL_ROOT_PASSWORD=*" } | Select-Object -First 1
if (-not $passwordEntry) {
    throw "The MySQL container has no MYSQL_ROOT_PASSWORD"
}
$rootPassword = $passwordEntry.Substring("MYSQL_ROOT_PASSWORD=".Length)

$databases = @(docker exec -e "MYSQL_PWD=$rootPassword" mysql mysql -uroot -N -e "SHOW DATABASES")
if ($LASTEXITCODE -ne 0) { throw "Could not inspect MySQL databases" }
if ($databases -notcontains $database) {
    docker exec -e "MYSQL_PWD=$rootPassword" mysql mysql -uroot -e "CREATE DATABASE $database"
    if ($LASTEXITCODE -ne 0) { throw "Could not create isolated test database" }
}
$tables = @(docker exec -e "MYSQL_PWD=$rootPassword" mysql mysql -uroot -N -e "SHOW TABLES FROM $database")
if ($LASTEXITCODE -ne 0 -or $tables.Count -ne 0) {
    throw "The isolated test database must be empty before running create-drop tests"
}

$redisSize = docker exec redis redis-cli -n 15 DBSIZE
if ($LASTEXITCODE -ne 0 -or $redisSize -ne "0") {
    throw "Redis DB 15 must be empty before running integration tests"
}

$previousPassword = [Environment]::GetEnvironmentVariable("CURRENCY_TEST_MYSQL_PASSWORD", "Process")
$previousFlag = [Environment]::GetEnvironmentVariable("RUN_REAL_INFRA_TESTS", "Process")
try {
    $env:CURRENCY_TEST_MYSQL_PASSWORD = $rootPassword
    $env:RUN_REAL_INFRA_TESTS = "true"
    Push-Location (Join-Path $PSScriptRoot "..")
    try {
        .\gradlew.bat test --no-daemon --rerun-tasks --tests org.example.backend.exchange.service.ExchangeSettlementRollbackIntegrationTest
        if ($LASTEXITCODE -ne 0) { throw "Real infrastructure tests failed" }
    } finally {
        Pop-Location
    }
} finally {
    [Environment]::SetEnvironmentVariable("CURRENCY_TEST_MYSQL_PASSWORD", $previousPassword, "Process")
    [Environment]::SetEnvironmentVariable("RUN_REAL_INFRA_TESTS", $previousFlag, "Process")
    $rootPassword = $null
}
