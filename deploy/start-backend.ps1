$ErrorActionPreference = 'Stop'
if (-not $env:MESSENGER_CONFIG) {
    $env:MESSENGER_CONFIG = Join-Path $PSScriptRoot 'config/messenger.properties'
}
if (-not (Test-Path -LiteralPath $env:MESSENGER_CONFIG -PathType Leaf)) {
    throw "Missing configuration: $env:MESSENGER_CONFIG"
}
& java -jar (Join-Path $PSScriptRoot 'messenger.jar') --spring.profiles.active=prod @args
exit $LASTEXITCODE
