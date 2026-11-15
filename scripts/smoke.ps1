param([string]$BaseUrl = 'http://localhost:5173')
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker with Compose v2 is required.' }
if (-not (Test-Path -LiteralPath .env)) { throw 'Copy .env.example to .env first.' }
$settings = @{}
Get-Content .env | ForEach-Object { if ($_ -match '^([A-Z_][A-Z0-9_]*)=(.*)$') { $settings[$matches[1]] = $matches[2] } }
if ($settings['SPRING_PROFILES_ACTIVE'] -ne 'dev') { throw 'Smoke tests require the dev profile and seed users.' }
function Invoke-Api([string]$Method, [string]$Path, $Body, [string]$Token, [int]$Expected = 200) {
    $arguments = @{ Uri = "$BaseUrl/api$Path"; Method = $Method; UseBasicParsing = $true }
    if ($null -ne $Body) { $arguments.ContentType = 'application/json'; $arguments.Body = ($Body | ConvertTo-Json -Compress -Depth 10) }
    if ($Token) { $arguments.Headers = @{ Authorization = "Bearer $Token" } }
    try { $response = Invoke-WebRequest @arguments; $status = [int]$response.StatusCode }
    catch {
        if ($null -eq $_.Exception.Response) { throw }
        $status = [int]$_.Exception.Response.StatusCode
        if ($status -ne $Expected) { throw }
        return $null
    }
    if ($status -ne $Expected) { throw "Expected $Expected for $Method $Path; got $status" }
    if ($response.Content) { return $response.Content | ConvertFrom-Json }
}
docker compose up --build --wait
if ($LASTEXITCODE -ne 0) { throw 'Compose startup failed.' }
$roles = @('employee', 'technician', 'admin')
foreach ($role in $roles) {
    $login = Invoke-Api 'POST' '/auth/login' @{ email = "$role@university.test"; password = $settings['DEV_SEED_PASSWORD'] } ''
    foreach ($target in $roles) {
        $expected = if ($role -eq $target) { 200 } else { 403 }
        $null = Invoke-Api 'GET' "/dashboards/$target" $null $login.accessToken $expected
    }
    $null = Invoke-Api 'POST' '/auth/logout' $null $login.accessToken 204
    $null = Invoke-Api 'GET' '/auth/me' $null $login.accessToken 401
}
$email = "smoke-$([Guid]::NewGuid().ToString('N'))@university.test"
$password = "Smoke!$([Guid]::NewGuid().ToString('N'))"
$registered = Invoke-Api 'POST' '/auth/register' @{ displayName = 'Smoke Test'; email = $email; password = $password; role = 'ADMIN' } '' 201
if ($registered.user.role -ne 'EMPLOYEE') { throw 'Public registration allowed role escalation.' }
$null = Invoke-Api 'GET' '/dashboards/employee' $null '' 401
$admin = Invoke-Api 'POST' '/auth/login' @{ email = 'admin@university.test'; password = $settings['DEV_SEED_PASSWORD'] } ''
$technician = Invoke-Api 'POST' '/auth/login' @{ email = 'technician@university.test'; password = $settings['DEV_SEED_PASSWORD'] } ''
$suffix = [Guid]::NewGuid().ToString('N')
$service = Invoke-Api 'POST' '/catalog/services' @{ name = "Smoke network $suffix"; description = 'Smoke test service'; businessCriticality = 3; active = $true } $admin.accessToken 201
$category = Invoke-Api 'POST' '/catalog/categories' @{ name = "Smoke category $suffix"; description = 'Smoke test category'; active = $true } $admin.accessToken 201
$profiles = Invoke-Api 'GET' '/admin/automation/technicians' $null $admin.accessToken
$originalProfile = $profiles | Where-Object { $_.id -eq $technician.user.id } | Select-Object -First 1
if ($null -eq $originalProfile) { throw 'The seed technician profile is missing.' }
$smokeSkills = @($originalProfile.skills) + @(@{ categoryId = $category.id; proficiency = 3 })
$smokeCapacity = [Math]::Max($originalProfile.capacity, $originalProfile.workload + 1)
$null = Invoke-Api 'PUT' "/admin/automation/technicians/$($technician.user.id)" @{ capacity = $smokeCapacity; available = $true; skills = $smokeSkills } $admin.accessToken
$incident = Invoke-Api 'POST' '/incidents' @{ title = 'Smoke workflow'; description = 'Verify complete incident lifecycle'; serviceId = $service.id; categoryId = $category.id; impact = 2; urgency = 3 } $registered.accessToken 201
$incidentPath = "/incidents/$($incident.incident.id)"
$inbox = Invoke-Api 'GET' '/notifications?size=100' $null $registered.accessToken
$message = $inbox.content | Where-Object { $_.incidentId -eq $incident.incident.id -and $_.action -eq 'CREATED' } | Select-Object -First 1
if ($null -eq $message -or $inbox.unreadCount -lt 1) { throw 'Committed creation notification missing.' }
$null = Invoke-Api 'POST' "/notifications/$($message.id)/read" @{ read = $true } $registered.accessToken
$null = Invoke-Api 'POST' "/notifications/$($message.id)/read" @{ read = $true } $technician.accessToken 404
$null = Invoke-Api 'GET' '/notifications/email-deliveries' $null $registered.accessToken 403
$deliveries = Invoke-Api 'GET' '/notifications/email-deliveries?size=100' $null $admin.accessToken
if (-not ($deliveries.content | Where-Object { $_.incidentId -eq $incident.incident.id -and $_.status -eq 'SIMULATED' })) { throw 'Simulated delivery log missing.' }
$suggestions = Invoke-Api 'POST' '/intelligence/suggestions' @{ title = 'Smoke workflow'; description = 'Verify complete incident lifecycle'; serviceId = $service.id } $registered.accessToken
if (-not ($suggestions.duplicates | Where-Object { $_.id -eq $incident.incident.id })) { throw 'Duplicate suggestion did not find the active same-service report.' }
$keyword = "smokerule$suffix"
$null = Invoke-Api 'PUT' '/intelligence/rules' @{ categoryId = $category.id; keyword = $keyword; weight = 25; active = $true } $admin.accessToken
$ruleSuggestion = Invoke-Api 'POST' '/intelligence/suggestions' @{ title = $keyword; description = "Categorize $keyword from persisted rules"; serviceId = $service.id } $registered.accessToken
if ($ruleSuggestion.categories[0].categoryId -ne $category.id) { throw 'Category recommendation did not use persisted rule.' }
$null = Invoke-Api 'PUT' '/intelligence/rules' @{ categoryId = $category.id; keyword = $keyword; weight = 25; active = $false } $admin.accessToken

if ($incident.incident.priority -ne 'P1' -or $incident.incident.priorityScore -ne 18) { throw 'Automatic priority calculation is incorrect.' }
if ($null -eq $incident.incident.sla.responseDueAt -or $null -eq $incident.incident.sla.resolutionDueAt) { throw 'Configure an active P1 SLA policy before running the smoke test.' }
$report = Invoke-Api 'GET' "/reports?id=$($incident.incident.id)&serviceId=$($service.id)" $null $admin.accessToken
if ($report.summary.total -ne 1 -or $report.summary.open -ne 1 -or $report.summary.critical -ne 1) { throw 'Database-backed report totals are incorrect.' }
$null = Invoke-Api 'GET' '/reports/technicians' $null $registered.accessToken 403
$null = Invoke-Api 'GET' '/reports/services' $null $technician.accessToken 403
$null = Invoke-Api 'GET' '/reports' $null '' 401
$null = Invoke-Api 'POST' "$incidentPath/classify" $null $registered.accessToken 403
if ($incident.incident.status -eq 'NEW') { $incident = Invoke-Api 'POST' "$incidentPath/classify" $null $admin.accessToken }
if ($incident.incident.status -eq 'CLASSIFIED') { $incident = Invoke-Api 'POST' "$incidentPath/auto-assign" $null $admin.accessToken }
if ($incident.incident.status -ne 'ASSIGNED' -or $incident.incident.technician.id -ne $technician.user.id) { throw 'Qualified strategy assignment failed.' }
$null = Invoke-Api 'POST' "$incidentPath/acknowledge" $null $technician.accessToken
$null = Invoke-Api 'POST' "$incidentPath/start" $null $technician.accessToken
$null = Invoke-Api 'POST' "$incidentPath/comments" @{ body = 'Private diagnostic output'; internal = $true } $technician.accessToken
$employeeDetail = Invoke-Api 'GET' $incidentPath $null $registered.accessToken
if ($employeeDetail.comments.Count -ne 0) { throw 'Internal notes leaked to the reporter.' }
$null = Invoke-Api 'POST' "$incidentPath/comments" @{ body = 'Connection restored'; internal = $false } $registered.accessToken
$null = Invoke-Api 'POST' "$incidentPath/resolve" @{ resolution = 'Replaced access point' } $technician.accessToken
$null = Invoke-Api 'POST' "$incidentPath/close" $null $admin.accessToken 409
$null = Invoke-Api 'POST' "$incidentPath/verify" $null $registered.accessToken
$null = Invoke-Api 'POST' "$incidentPath/feedback" @{ rating = 5; feedback = 'Working again' } $registered.accessToken
$closed = Invoke-Api 'POST' "$incidentPath/close" $null $registered.accessToken
if ($closed.incident.status -ne 'CLOSED') { throw 'Incident closure failed.' }
$reportQuery = "id=$($incident.incident.id)&status=CLOSED&priority=P1&categoryId=$($category.id)&serviceId=$($service.id)&technicianId=$($technician.user.id)&slaStatus=MET"
$report = Invoke-Api 'GET' "/reports?$reportQuery" $null $registered.accessToken
if ($report.summary.total -ne 1 -or $report.summary.open -ne 0 -or $report.summary.resolved -ne 1 -or $report.summary.compliancePercent -ne 100 -or $null -eq $report.summary.averageResolutionSeconds) { throw 'Resolved report aggregation or completed SLA compliance is incorrect.' }
$rows = Invoke-Api 'GET' "/incidents?$reportQuery&sort=id&direction=asc&page=0&size=1" $null $admin.accessToken
if ($rows.totalElements -ne 1 -or $rows.content[0].id -ne $incident.incident.id) { throw 'Combined SLA filtering/sort/pagination failed.' }
$csv = Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/api/reports/incidents.csv?$reportQuery&sort=id&direction=asc" -Headers @{ Authorization = "Bearer $($registered.accessToken)" }
if ($csv.StatusCode -ne 200 -or $csv.Headers['Content-Type'] -notlike 'text/csv*' -or $csv.Content -notmatch 'Smoke workflow' -or $csv.Content -notmatch 'Incident ID') { throw 'Authorized filtered CSV export failed.' }
$workload = Invoke-Api 'GET' "/reports/technicians?technicianId=$($technician.user.id)&serviceId=$($service.id)" $null $admin.accessToken
if ($workload.totalElements -ne 1 -or $workload.content[0].resolved -ne 1) { throw 'Technician resolution analytics are incorrect.' }

$null = Invoke-Api 'PUT' "/admin/automation/technicians/$($technician.user.id)" @{ capacity = $originalProfile.capacity; available = $originalProfile.available; skills = @($originalProfile.skills) } $admin.accessToken
$null = Invoke-Api 'POST' '/auth/logout' $null $technician.accessToken 204
$null = Invoke-Api 'POST' '/auth/logout' $null $admin.accessToken 204
docker compose stop backend db
if ($LASTEXITCODE -ne 0) { throw 'Could not stop services for persistence verification.' }
docker compose up -d --wait db backend
if ($LASTEXITCODE -ne 0) { throw 'Could not restart services for persistence verification.' }
$login = Invoke-Api 'POST' '/auth/login' @{ email = $email; password = $password } ''
$null = Invoke-Api 'GET' '/auth/me' $null $login.accessToken
$persisted = Invoke-Api 'GET' $incidentPath $null $login.accessToken
if ($persisted.incident.status -ne 'CLOSED' -or $persisted.incident.feedbackRating -ne 5 -or $persisted.history.Count -lt 10 -or $persisted.incident.priority -ne 'P1' -or $null -eq $persisted.incident.sla.firstAcknowledgedAt) { throw 'Incident/history/priority/SLA did not persist through restart.' }
$persistedInbox = Invoke-Api 'GET' '/notifications?size=100' $null $login.accessToken
$persistedMessage = $persistedInbox.content | Where-Object { $_.id -eq $message.id } | Select-Object -First 1
if ($null -eq $persistedMessage -or $null -eq $persistedMessage.readAt) { throw 'Notification read state did not survive restart.' }
if (-not ($persisted.history | Where-Object { $_.action -eq 'CLOSE' -and $_.newValues.status -eq 'CLOSED' -and $_.oldValues.status -eq 'VERIFIED' })) { throw 'Structured closure audit did not persist.' }
$null = Invoke-Api 'POST' '/auth/logout' $null $registered.accessToken 204
$null = Invoke-Api 'POST' '/auth/logout' $null $login.accessToken 204
$persistedReport = Invoke-Api 'POST' '/auth/login' @{ email = $email; password = $password } ''
$report = Invoke-Api 'GET' "/reports?$reportQuery" $null $persistedReport.accessToken
if ($report.summary.resolved -ne 1) { throw 'Report source data did not persist.' }
$null = Invoke-Api 'POST' '/auth/logout' $null $persistedReport.accessToken 204
Write-Host 'PASS: reporting aggregation, combined filters/sorting/pagination, scoped CSV export, workload analytics, Compose startup, authentication/RBAC, incident lifecycle, calculated priority, qualified assignment, SLA tracking, internal-note privacy, token revocation notifications/read state, simulated delivery logs, duplicate/category suggestions, structured audit and persistence after database/backend restart.'
