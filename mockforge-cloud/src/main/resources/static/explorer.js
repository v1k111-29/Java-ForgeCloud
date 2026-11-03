// A small, curated API reference for the existing Spring Boot controllers.
// Find an existing control using its ID from the HTML file.
function getPageElement(elementId) {

    return document.getElementById(elementId);
}


let workspaceId = 1;

let endpointId = 1;

let runtimePanel;

let loadingRequestLogs = false;


async function fetchJsonResponse(requestPath) {
    const response = await fetch(requestPath, {
        cache: 'no-store',

        signal: AbortSignal.timeout(5000)
    });

    const responseData = await response.json();

    if (!response.ok) {
        throw new Error(responseData.error || 'HTTP ' + response.status);
    }

    return responseData;
}


// Keep the API examples together so they are easy to compare with the Java controllers.
function getApiOperations() {
    return [

        {
            id: 'list-workspaces',

            group: 'Workspaces',

            method: 'GET',

            path: '/api/workspaces',

            title: 'List workspaces',

            description: 'Returns the workspaces stored in MySQL.'
        },
        {
            id: 'create-workspace',

            group: 'Workspaces',

            method: 'POST',

            path: '/api/workspaces',

            title: 'Create a workspace',

            description: 'Requires a name; description is optional. Successful creation returns 201 and the generated ID.',

            body: {
                name: 'API Explorer Demo',

                description: 'Created through the interactive API reference'
            }
        },
        {
            id: 'list-endpoints',

            group: 'Endpoints',

            method: 'GET',

            path: '/api/endpoints',

            title: 'List all endpoint definitions',

            description: 'Returns the configured endpoint definitions across workspaces.'
        },
        {
            id: 'workspace-endpoints',

            group: 'Endpoints',

            method: 'GET',

            path: '/api/workspaces/{workspaceId}/endpoints',

            title: 'List a workspace’s endpoints',

            description: 'A missing workspace returns 404. Alias: /api/endpoints/workspace/{workspaceId}.'
        },
        {
            id: 'create-workspace-endpoint',

            group: 'Endpoints',

            method: 'POST',

            path: '/api/workspaces/{workspaceId}/endpoints',

            title: 'Create an endpoint in a workspace',

            description: 'Document-compatible create route. Ownership comes from the URL. name/routePath are accepted alongside endpointName/mockUrlPath. Both names are returned for compatibility.',

            body: {
                name: 'Document example',

                routePath: '/document-example',

                httpMethod: 'GET',

                responseStatus: 200,

                responseContentType: 'application/json',

                responseBody: '{"message":"Document-compatible API"}',

                executionMode: 'MOCK'
            }
        },
        {
            id: 'create-endpoint',

            group: 'Endpoints',

            method: 'POST',

            path: '/api/endpoints',

            title: 'Create a mock endpoint (compatible route)',

            description: 'Existing create route retained for current clients. Save a response definition. Duplicate workspace + method + path returns 409. An update API is not implemented.',

            body: {
                workspaceId,

                endpointName: 'Explorer example',

                mockUrlPath: '/hello-explorer',

                httpMethod: 'GET',

                responseStatus: 200,

                responseContentType: 'application/json',

                responseBody: '{"message":"Hello from MockForge"}',

                executionMode: 'MOCK'
            }
        },
        {
            id: 'toggle-endpoint',

            group: 'Endpoints',

            method: 'PATCH',

            path: '/api/endpoints/{endpointId}/enabled',

            title: 'Enable or disable an endpoint',

            description: 'Changes whether the endpoint can match incoming mock requests.',

            body: {
                enabled: false
            }
        },
        {
            id: 'execute-mock',

            group: 'Mock runtime',

            method: 'ANY',

            path: '/mock/workspaces/{workspaceId}/{route}',

            title: 'Execute a configured mock route',

            description: 'Matches workspace, HTTP method, path and enabled state. PROXY_FALLBACK supports approved GET upstreams; only timeout triggers configured fallback. Upstream HTTP errors pass through.'
        },
        {
            id: 'endpoint-logs',

            group: 'Request logs',

            method: 'GET',

            path: '/api/endpoints/{endpointId}/logs',

            title: 'Read an endpoint’s request logs',

            description: 'Newest first. The requested limit is clamped to 1–100.',

            query: {
                limit: '50'
            }
        },
        {
            id: 'recent-logs',

            group: 'Request logs',

            method: 'GET',

            path: '/api/logs',

            title: 'Read the latest 50 logs',

            description: 'Request logging is asynchronous. A response can finish before its log appears.'
        },
        {
            id: 'logging-stats',

            group: 'Request logs',

            method: 'GET',

            path: '/api/logging/stats',

            title: 'Read queue statistics',

            description: 'Returns queued, saved, rejected and failed log counts.'
        }
    ];
}


// Give each input a label so it is clear what the caller needs to enter.
function addFormField(container, responseText, control) {
    const label = document.createElement('label');

    label.append(document.createTextNode(responseText), control);

    container.append(label);

    return label;
}


// Build one expandable API card, including its inputs and response area.
function createOperationPanel(operationDefinition) {
    const operationPanel = document.createElement('details');

    operationPanel.id = operationDefinition.id;

    operationPanel.className = 'operation ' + operationDefinition.method.toLowerCase();

    const operationSummary = document.createElement('summary');

    const methodBadge = document.createElement('span');
    methodBadge.className = 'method';
    methodBadge.textContent = operationDefinition.method;

    const requestPath = document.createElement('code');
    requestPath.textContent = operationDefinition.path;

    const titleElement = document.createElement('span');
    titleElement.className = 'summaryText';
    titleElement.textContent = operationDefinition.title;

    const chevron = document.createElement('span');
    chevron.className = 'chevron';
    chevron.textContent = '⌄';

    operationSummary.append(methodBadge, requestPath, titleElement, chevron);

    const operationContent = document.createElement('div');
    operationContent.className = 'operationBody';

    const tryButtonRow = document.createElement('div');
    tryButtonRow.className = 'tryRow';

    const descriptionElement = document.createElement('p');
    descriptionElement.textContent = operationDefinition.description;

    const tryButton = document.createElement('button');
    tryButton.textContent = 'Try it out';

    tryButtonRow.append(descriptionElement, tryButton);

    const parameterFields = document.createElement('fieldset');
    parameterFields.className = 'parameters';
    parameterFields.disabled = true;

    const parameterInputs = {
    };

    for (const match of operationDefinition.path.matchAll(/\{(\w+)\}/g)) {
        const name = match[1];

        const inputControl = document.createElement('input');

        inputControl.dataset.parameter = name;

        inputControl.value = name === 'workspaceId' ? workspaceId : name === 'endpointId' ? endpointId : '/products';

        if (name !== 'route') {
            inputControl.type = 'number';
            inputControl.min = '1';
            inputControl.step = '1';
        }

        addFormField(parameterFields, name + ' (path, required)', inputControl);

        parameterInputs[name] = inputControl;
    }

    let methodInput;

    if (operationDefinition.method === 'ANY') {
        methodInput = document.createElement('select');

        for (const httpMethod of [
            'GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'
        ]) {
            methodInput.add(new Option(httpMethod, httpMethod));
        }

        addFormField(parameterFields, 'HTTP method', methodInput);
    }

    const queryInputs = {
    };

    for (const [name, value] of Object.entries(operationDefinition.query || {
    })) {
        const inputControl = document.createElement('input');
        inputControl.value = value;

        addFormField(parameterFields, name + ' (query)', inputControl);
        queryInputs[name] = inputControl;
    }

    let requestBody;

    if (operationDefinition.body || operationDefinition.method === 'ANY') {
        requestBody = document.createElement('textarea');
        requestBody.rows = 8;
        requestBody.spellcheck = false;

        requestBody.value = operationDefinition.body ? JSON.stringify(operationDefinition.body, null, 2) : '';

        const label = addFormField(parameterFields, 'Request body · application/json', requestBody);
        label.className = 'bodyField';
    }

    const executeButton = document.createElement('button');
    executeButton.className = 'execute';
    executeButton.textContent = 'Execute';
    executeButton.hidden = true;

    const responseResult = document.createElement('div');
    responseResult.className = 'result';
    responseResult.hidden = true;

    const requestUrl = document.createElement('div');
    requestUrl.className = 'requestUrl';

    const responseMetadata = document.createElement('div');
    responseMetadata.className = 'resultMeta';

    const responseBody = document.createElement('pre');

    const headerDetails = document.createElement('details');

    const headerSummary = document.createElement('summary');
    headerSummary.textContent = 'Response headers';

    const responseHeaders = document.createElement('pre');
    headerDetails.append(headerSummary, responseHeaders);

    responseResult.append(requestUrl, responseMetadata, responseBody, headerDetails);

    tryButton.onclick = () => {
        parameterFields.disabled = false;
        executeButton.hidden = false;
        tryButton.textContent = 'Parameters enabled';
    };

    executeButton.onclick = async () => {
        executeButton.disabled = true;
        responseResult.hidden = false;

        responseMetadata.textContent = 'Sending request…';
        responseBody.textContent = '';
        responseHeaders.textContent = '';

        try {
            let route = operationDefinition.path.replace(/\{(\w+)\}/g, (placeholder, name) => {
                const value = parameterInputs[name].value.trim();

                if (name === 'route') {
                    if (!value.startsWith('/') || /[?#%]/.test(value) || value.includes('//')) {
                        throw new Error('Use a plain route such as /products.');
                    }

                    return value.replace(/^\//, '');
                }

                if (!Number.isSafeInteger(Number(value)) || Number(value) < 1) {
                    throw new Error(name + ' must be a positive integer.');
                }

                return encodeURIComponent(value);
            });

            const queryParameters = new URLSearchParams();

            for (const [name, inputControl] of Object.entries(queryInputs)) if (inputControl.value.trim()) {
                queryParameters.set(name, inputControl.value.trim());
            }

            if (queryParameters.size) {
                route += '?' + queryParameters;
            }

            const httpMethod = methodInput ? methodInput.value : operationDefinition.method;

            requestUrl.textContent = httpMethod + ' ' + location.origin + route;

            const requestStartTime = performance.now();

            const response = await fetch(route, {
                method: httpMethod,

                cache: 'no-store',

                signal: AbortSignal.timeout(15000),

                headers: {
                    'Content-Type': 'application/json'
                },

                body: httpMethod === 'GET' || httpMethod === 'HEAD' || !requestBody?.value ? undefined : requestBody.value
            });

            const responseText = await response.text();

            const requestDurationMilliseconds = Math.round(performance.now() - requestStartTime);

            responseMetadata.replaceChildren();

            for (const value of [
                'HTTP ' + response.status, requestDurationMilliseconds + ' ms', response.headers.get('X-MockForge-Source'), response.headers.get('X-MockForge-Reason')
            ].filter(Boolean)) {
                const item = document.createElement('strong');
                item.textContent = value;
                responseMetadata.append(item);
            }

            try {
                responseBody.textContent = JSON.stringify(JSON.parse(responseText), null, 2);
            } catch (error) {
                responseBody.textContent = responseText || '(empty response)';
            }

            responseHeaders.textContent = Array.from(response.headers, ([name, value]) => name + ': ' + value).join('\n');

            await refreshLogs();
        } catch (error) {
            responseMetadata.textContent = 'Request failed';
            responseBody.textContent = error.message;
        } finally {
            executeButton.disabled = false;
        }
    };

    operationContent.append(tryButtonRow, parameterFields, executeButton, responseResult);
    operationPanel.append(operationSummary, operationContent);

    if (operationDefinition.id === 'execute-mock') {
        runtimePanel = {
            panel: operationPanel,

            parameters: parameterInputs,

            fields: parameterFields,

            execute: executeButton,

            tryButton,

            methodInput
        };
    }

    return operationPanel;
}


// Read the demo controls before opening the shop or loading a runtime request.
function getSelectedDemo() {
    const workspace = Number(getPageElement('demoWorkspace').value);

    const route = getPageElement('demoSource').value === 'custom' ? getPageElement('demoRoute').value.trim() : getPageElement('demoSource').value;

    if (!Number.isSafeInteger(workspace) || workspace < 1 || !route.startsWith('/') || /[?#%]/.test(route) || route.includes('//')) {
        throw new Error('Enter a positive workspace ID and a plain route beginning with /.');
    }

    return {
        workspace,

        route
    };
}


function updateShopLink() {
    try {
        const selectedDemo = getSelectedDemo();

        getPageElement('openShop').href = '/shop.html?' + new URLSearchParams({
            workspaceId: selectedDemo.workspace,

            route: selectedDemo.route
        });
    } catch (error) {
        getPageElement('demoNotice').textContent = error.message;
    }
}

getPageElement('loadDemo').onclick = () => {
    try {
        const selectedDemo = getSelectedDemo();

        runtimePanel.panel.open = true;

        runtimePanel.fields.disabled = false;
        runtimePanel.execute.hidden = false;

        runtimePanel.tryButton.textContent = 'Parameters enabled';

        runtimePanel.parameters.workspaceId.value = selectedDemo.workspace;

        runtimePanel.parameters.route.value = selectedDemo.route;

        runtimePanel.methodInput.value = 'GET';

        runtimePanel.panel.scrollIntoView({
            behavior: 'smooth',

            block: 'start'
        });

        getPageElement('demoNotice').textContent = 'Runtime request loaded. Click Execute to inspect it, or Open customer shop to see the consumer.';

        updateShopLink();
    } catch (error) {
        getPageElement('demoNotice').textContent = error.message;
    }
};

for (const elementId of [
    'demoWorkspace', 'demoSource', 'demoRoute'
]) {
    getPageElement(elementId).oninput = updateShopLink;
}

getPageElement('openShop').onclick = event => {
    try {
        getSelectedDemo();
        updateShopLink();
    } catch (error) {
        event.preventDefault();
        getPageElement('demoNotice').textContent = error.message;
    }
};


// Refresh the recent activity table without starting a second overlapping request.
async function refreshLogs() {
    if (loadingRequestLogs) {
        return;
    }

    loadingRequestLogs = true;

    try {
        const requestLogs = await fetchJsonResponse('/api/logs');

        getPageElement('logRows').replaceChildren();

        for (const requestLog of requestLogs.slice(0, 10)) {
            const row = document.createElement('tr');

            for (const value of [
                requestLog.logId, requestLog.endpointId, requestLog.httpMethod + ' ' + requestLog.requestPath, requestLog.responseStatus, requestLog.responseSource, requestLog.fallbackReason || '—', requestLog.durationMs + ' ms'
            ]) {
                const tableCell = document.createElement('td');
                tableCell.textContent = value;
                row.append(tableCell);
            }

            getPageElement('logRows').append(row);
        }

        getPageElement('logStatus').textContent = 'Latest global request logs · updated ' + new Date().toLocaleTimeString();
    } catch (error) {
        getPageElement('logStatus').textContent = 'Could not fetch logs: ' + error.message;
    } finally {
        loadingRequestLogs = false;
    }
}


// Find the demo workspace first, then build the cards with useful starting IDs.
async function initializeApiExplorer() {
    getPageElement('origin').textContent = location.origin;

    try {
        const workspaceRecords = await fetchJsonResponse('/api/workspaces');

        workspaceId = workspaceRecords.find(workspace => workspace.name === 'Shopping Demo')?.workspaceId || workspaceRecords[0]?.workspaceId || 1;

        const endpointRecords = await fetchJsonResponse('/api/endpoints/workspace/' + workspaceId);

        endpointId = endpointRecords.find(endpoint => endpoint.mockUrlPath === '/products')?.endpointId || endpointRecords[0]?.endpointId || 1;

        getPageElement('demoNotice').textContent = 'Shopping Demo workspace: ' + workspaceId + '. Java backend and saved data are shared by both frontends.';
    } catch (error) {
        getPageElement('demoNotice').textContent = 'Start the backend and run tools/seed_demo.ps1. ' + error.message;
    }

    getPageElement('demoWorkspace').value = workspaceId;

    let operationGroup;

    for (const operationDefinition of getApiOperations()) {
        if (operationGroup !== operationDefinition.group) {
            operationGroup = operationDefinition.group;

            const heading = document.createElement('h2');
            heading.className = 'tagHeading';
            heading.textContent = operationGroup;

            getPageElement('operations').append(heading);
        }

        getPageElement('operations').append(createOperationPanel(operationDefinition));
    }

    updateShopLink();
    refreshLogs();
}

initializeApiExplorer();

setInterval(() => {
    if (getPageElement('pollLogs').checked) {
        refreshLogs();
    }
}, 2000);
