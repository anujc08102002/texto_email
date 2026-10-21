param(
    [ValidateSet('all','backend','frontend')][string]$Service='all',
    [ValidateSet('mailpit','ses')][string]$Delivery='mailpit',
    [ValidatePattern('^[A-Za-z0-9_.-]*$')][string]$AwsProfile=''
)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
# Explicit process-only development configuration. Never imports production .env values.
$env:SPRING_PROFILES_ACTIVE='local'
$env:DATABASE_URL='jdbc:postgresql://127.0.0.1:5433/texto_local_workflow'
$env:DATABASE_USERNAME='email_platform'
$env:DATABASE_PASSWORD='email_platform'
$env:RABBITMQ_HOST='127.0.0.1'
$env:RABBITMQ_PORT='5672'
$env:RABBITMQ_USERNAME='email_platform'
$env:RABBITMQ_PASSWORD='email_platform'
$env:REDIS_HOST='127.0.0.1'
$env:REDIS_PORT='6379'
$env:SPRING_DATA_REDIS_PASSWORD=''
$env:MTA_IMPLEMENTATION='mailpit'
$env:MAILPIT_HOST='127.0.0.1'
$env:MAILPIT_SMTP_PORT='1025'
$env:MTA_SMTP_HOST='127.0.0.1'
$env:MTA_SMTP_PORT='1025'
$env:MTA_SMTP_SSL_ENABLED='false'
$env:MTA_SMTP_STARTTLS_ENABLED='false'
$env:MTA_SMTP_STARTTLS_REQUIRED='false'
$env:SES_ENABLED='false'
$env:SES_FEEDBACK_ENABLED='false'
$env:SES_REGION='ap-south-1'
$env:SES_CONFIGURATION_SET_NAME='my-first-configuration-set'
$env:EMAIL_PUBLIC_DELIVERY_ENABLED='false'
$env:EMAIL_PUBLIC_BASE_URL='http://localhost:8080'
$env:BOUNCE_DOMAIN='bounce.texto.test'
$env:ALLOW_PLATFORM_TEST_SENDERS='true'
$env:CORS_ALLOWED_ORIGINS='http://localhost:3000,http://127.0.0.1:3000'
$env:NEXT_PUBLIC_API_BASE_URL='http://localhost:8080'
if($Delivery -eq 'ses') {
    $env:MTA_IMPLEMENTATION='ses'
    $env:SES_ENABLED='true'
    $env:EMAIL_PUBLIC_DELIVERY_ENABLED='true'
    $env:ALLOW_PLATFORM_TEST_SENDERS='false'
    $env:BOUNCE_DOMAIN='bounce.texto-qa.in'
    if($AwsProfile) { $env:AWS_PROFILE=$AwsProfile }
    Write-Host 'Real email delivery: Amazon SES (ap-south-1). Use a verified sender domain and real recipients.'
}
if($Service -eq 'backend') {
    Set-Location -LiteralPath (Join-Path $projectRoot 'backend')
    & .\mvnw.cmd '-Dspring-boot.run.arguments=--server.address=127.0.0.1' spring-boot:run
    exit $LASTEXITCODE
}
if($Service -eq 'frontend') {
    Set-Location -LiteralPath (Join-Path $projectRoot 'frontend')
    & npm.cmd run dev -- --hostname 127.0.0.1
    exit $LASTEXITCODE
}
& docker compose --env-file .env.example -f infrastructure/docker-compose.yml up -d postgres rabbitmq redis mailpit
if($LASTEXITCODE -ne 0){throw 'Start Docker Desktop and ensure the local Docker engine is accessible.'}
$database=& docker exec email-platform-postgres psql -U email_platform -d email_platform -Atc "SELECT datname FROM pg_database WHERE datname = 'texto_local_workflow'"
if($LASTEXITCODE -ne 0){throw 'Unable to reach local PostgreSQL. Verify its configured local credentials.'}
if($database -notcontains 'texto_local_workflow') {
    & docker exec email-platform-postgres createdb -U email_platform texto_local_workflow
    if($LASTEXITCODE -ne 0){throw 'Unable to create the dedicated local workflow database.'}
}
$runtime=Join-Path $projectRoot 'backend/target'
New-Item -ItemType Directory -Path $runtime -Force | Out-Null
function Test-LocalPort([int]$Port) {
    $client=[Net.Sockets.TcpClient]::new()
    try{$client.Connect('127.0.0.1',$Port);return $true}catch{return $false}finally{$client.Dispose()}
}
foreach($entry in @(@{Name='backend';Port=8080},@{Name='frontend';Port=3000})) {
    if(Test-LocalPort $entry.Port) { Write-Host "$($entry.Name) port $($entry.Port) is already in use; keeping the existing process.";continue }
    $shell=(Get-Process -Id $PID).Path
    $helperArgs=@('-NoProfile','-File',('"'+$PSCommandPath+'"'),'-Service',$entry.Name,'-Delivery',$Delivery)
    if($AwsProfile) { $helperArgs += @('-AwsProfile',$AwsProfile) }
    $started=Start-Process -FilePath $shell -ArgumentList $helperArgs -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtime "local-$($entry.Name).stdout.log") -RedirectStandardError (Join-Path $runtime "local-$($entry.Name).stderr.log")
    Write-Host "Started local $($entry.Name) helper PID $($started.Id)."
}
Write-Host 'Frontend: http://localhost:3000'
Write-Host 'Backend health: http://localhost:8080/actuator/health'
if($Delivery -eq 'mailpit') {
    Write-Host 'Captured test emails: http://localhost:8025'
    Write-Host 'Verify after startup: python scripts/verify-local-workflow.py'
} else {
    Write-Host 'SES mode requires AWS credentials (local profile, environment, or an EC2 instance role).'
    Write-Host 'An already-running backend keeps its current provider; stop it before switching delivery modes.'
}
