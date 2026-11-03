param(
    [string]$BaseUrl = 'http://localhost:8080'
)

$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')

function Invoke-DemoApi {
    param([string]$Method, [string]$Path, $Body = $null)

    $request = @{
        Uri = $BaseUrl + $Path
        Method = $Method
        TimeoutSec = 15
    }
    if ($null -ne $Body) {
        $request.ContentType = 'application/json; charset=utf-8'
        $request.Body = [System.Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 10))
    }
    Invoke-RestMethod @request
}

# Reuse the named workspace instead of creating another one every run.
$workspaces = Invoke-DemoApi 'GET' '/api/workspaces'
$workspace = $workspaces | Where-Object { $_.name -eq 'Shopping Demo' } | Select-Object -First 1
if ($null -eq $workspace) {
    $workspace = Invoke-DemoApi 'POST' '/api/workspaces' @{
        name = 'Shopping Demo'
        description = 'Demo A: endpoint tester. Demo B: shop consumer and configured timeout fallback.'
    }
    Write-Host 'Created Shopping Demo workspace.'
} else {
    Write-Host 'Reusing Shopping Demo workspace.'
}

$workspaceId = $workspace.workspaceId
$products = @(
    @{ id = 1; name = 'Everyday Notebook'; price = 6.50 },
    @{ id = 2; name = 'Desk Lamp'; price = 24.00 },
    @{ id = 3; name = 'Wireless Headphones'; price = 49.00 },
    @{ id = 4; name = 'Canvas Backpack'; price = 32.00 },
    @{ id = 5; name = 'Travel Mug'; price = 12.00 }
)
$productJson = ConvertTo-Json -InputObject $products -Depth 5 -Compress

$definitions = @(
    @{ name = 'Mock product catalog'; path = '/products'; mode = 'MOCK'; upstream = $null },
    @{ name = 'Fast upstream products'; path = '/products/live'; mode = 'PROXY_FALLBACK'; upstream = 'http://localhost:9001/products' },
    @{ name = 'Slow upstream products'; path = '/products/slow'; mode = 'PROXY_FALLBACK'; upstream = 'http://localhost:9001/slow' },
    @{ name = 'Broken upstream products'; path = '/products/broken'; mode = 'PROXY_FALLBACK'; upstream = 'http://localhost:9001/error' }
)

foreach ($definition in $definitions) {
    $body = @{
        workspaceId = $workspaceId
        endpointName = $definition.name
        mockUrlPath = $definition.path
        httpMethod = 'GET'
        responseStatus = 200
        responseContentType = 'application/json'
        responseBody = $productJson
        executionMode = $definition.mode
        upstreamUrl = $definition.upstream
        timeoutMs = 1500
    }
    try {
        $endpoint = Invoke-DemoApi 'POST' '/api/endpoints' $body
        Write-Host ('Created GET {0} (endpoint {1})' -f $definition.path, $endpoint.endpointId)
    } catch {
        $status = $null
        if ($null -ne $_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
        }
        if ($status -ne 409) { throw }
        Write-Host ('GET {0} already exists; leaving its configuration unchanged.' -f $definition.path)
    }
}

# Check reused definitions as well; never claim an incompatible existing endpoint is ready.
$endpoints = Invoke-DemoApi 'GET' ("/api/endpoints/workspace/$workspaceId")
foreach ($definition in $definitions) {
    $saved = $endpoints | Where-Object { $_.httpMethod -eq 'GET' -and $_.mockUrlPath -ceq $definition.path } | Select-Object -First 1
    if ($null -eq $saved) { throw ('Missing endpoint: ' + $definition.path) }
    if (!$saved.enabled -or $saved.executionMode -ne $definition.mode -or
            ($definition.mode -eq 'PROXY_FALLBACK' -and ($saved.upstreamUrl -ne $definition.upstream -or $saved.timeoutMs -ne 1500))) {
        Write-Warning ('Existing endpoint ' + $definition.path + ' differs from the demo settings. Inspect it in the tester.')
    }
}

Write-Host ''
Write-Host ("Shopping Demo workspace ID: $workspaceId")
Write-Host ("Open $BaseUrl/shop.html?workspaceId=$workspaceId")
Write-Host 'Without a query parameter, the page finds Shopping Demo by name.'
Write-Host 'No existing workspace, endpoint or request log was deleted.'
