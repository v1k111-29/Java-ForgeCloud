// Find an existing control using its ID from the HTML file.
function getPageElement(elementId) {

    return document.getElementById(elementId);
}


const requestHistory = [];


// Send the management request and show the server error if it was rejected.
async function callApi(requestPath, httpMethod = 'GET', requestData) {
    const response = await fetch(requestPath, {
        method: httpMethod,

        headers: {
            'Content-Type': 'application/json'
        },

        body: requestData === undefined ? undefined : JSON.stringify(requestData)
    });

    const responseText = await response.text();

    if (!response.ok) {
        throw new Error(responseText);
    }

    return responseText ? JSON.parse(responseText) : null;
}


function showError(error) {
    getPageElement('notice').textContent = error.message;
}


// Loading a request fills the form; the user still chooses when to send it.
function loadRequest(httpMethod, requestPath, requestBody = '') {
    getPageElement('requestMethod').value = httpMethod;

    getPageElement('requestPath').value = requestPath;

    getPageElement('requestBody').value = requestBody;

    getPageElement('notice').textContent = 'Request loaded. Edit it if needed, then click Send request.';
}


// Keep the last few requests available while this tab is open.
function renderHistory() {
    getPageElement('history').replaceChildren();

    for (const request of requestHistory) {
        const item = document.createElement('li');

        const button = document.createElement('button');

        button.className = 'secondary';

        button.textContent = request.method + ' ' + request.path + ' → ' + request.status;

        button.onclick = () => loadRequest(request.method, request.path, request.body);

        item.append(button);

        getPageElement('history').append(item);
    }
}

// Send real HTTP requests to this application's API and mock routes.
getPageElement('requestForm').onsubmit = async event => {
    event.preventDefault();

    getPageElement('send').disabled = true;

    getPageElement('notice').textContent = '';

    getPageElement('responseStatus').textContent = 'Sending…';

    getPageElement('responseBody').textContent = '';

    getPageElement('responseHeaders').textContent = '';

    getPageElement('responseTime').textContent = '';

    getPageElement('responseSize').textContent = '';

    const requestAbortController = new AbortController();

    const requestTimeout = setTimeout(() => requestAbortController.abort(), 15000);

    try {
        const httpMethod = getPageElement('requestMethod').value;

        const inputControl = getPageElement('requestPath').value.trim();

        const requestUrl = new URL(inputControl, window.location.origin);

        if (requestUrl.origin !== window.location.origin || !/^\/(api|mock)(\/|$)/.test(requestUrl.pathname)) {
            throw new Error('Use an /api or /mock URL on this MockForge server.');
        }

        const requestHeaders = JSON.parse(getPageElement('requestHeaders').value || '{}');

        if (!requestHeaders || Array.isArray(requestHeaders) || typeof requestHeaders !== 'object') {
            throw new Error('Headers must be a JSON object.');
        }

        for (const value of Object.values(requestHeaders)) {
            if (typeof value !== 'string') {
                throw new Error('Header values must be strings.');
            }
        }

        const requestBody = getPageElement('requestBody').value;

        const requestStartTime = performance.now();

        const response = await fetch(requestUrl, {
            method: httpMethod,

            headers: requestHeaders,

            signal: requestAbortController.signal,

            cache: 'no-store',

            body: httpMethod === 'GET' || httpMethod === 'HEAD' || !requestBody ? undefined : requestBody
        });

        const responseText = await response.text();

        const requestDurationMilliseconds = Math.round(performance.now() - requestStartTime);

        getPageElement('responseStatus').textContent = response.status + ' ' + response.statusText;

        getPageElement('responseTime').textContent = requestDurationMilliseconds + ' ms';

        getPageElement('responseSize').textContent = new TextEncoder().encode(responseText).length + ' bytes';

        try {
            getPageElement('responseBody').textContent = JSON.stringify(JSON.parse(responseText), null, 2);
        } catch (error) {
            getPageElement('responseBody').textContent = responseText || '(empty response body)';
        }

        getPageElement('responseHeaders').textContent = Array.from(response.headers, ([key, value]) => key + ': ' + value).join('\n');

        requestHistory.unshift({
            method: httpMethod,

            path: requestUrl.pathname + requestUrl.search,

            body: requestBody,

            status: response.status
        });

        if (requestHistory.length > 10) {
            requestHistory.pop();
        }

        renderHistory();

        if (httpMethod !== 'GET' && httpMethod !== 'HEAD') {
            await loadWorkspaces(getPageElement('workspace').value);
        }
    } catch (error) {
        getPageElement('responseStatus').textContent = 'Request not completed';

        showError(error.name === 'AbortError' ? new Error('Request exceeded the 15-second client timeout.') : error);
    } finally {
        clearTimeout(requestTimeout);

        getPageElement('send').disabled = false;
    }
};


// Refresh the workspace selector, then load the selected workspace's endpoints.
async function loadWorkspaces(selectedWorkspaceId) {
    const workspaceRecords = await callApi('/api/workspaces');

    getPageElement('workspace').replaceChildren();

    for (const workspace of workspaceRecords) {
        getPageElement('workspace').add(new Option(workspace.name, workspace.workspaceId));
    }

    if (selectedWorkspaceId && workspaceRecords.some(workspace => String(workspace.workspaceId) === String(selectedWorkspaceId))) {
        getPageElement('workspace').value = selectedWorkspaceId;
    }

    await loadEndpoints();
}


// Each saved definition gets buttons for testing it, reading its logs and toggling it.
async function loadEndpoints() {
    getPageElement('endpoints').replaceChildren();

    const workspaceId = getPageElement('workspace').value;

    if (!workspaceId) {
        return;
    }

    for (const endpoint of await callApi('/api/endpoints/workspace/' + workspaceId)) {
        const item = document.createElement('li');

        const titleElement = document.createElement('div');

        titleElement.textContent = endpoint.httpMethod + ' ' + endpoint.mockUrlPath + ' · ' + endpoint.executionMode + ' · ' + (endpoint.enabled ? 'enabled' : 'disabled');

        item.append(titleElement);

        const supportedExecutionMode = [
            'MOCK', 'PROXY_FALLBACK'
        ].includes(endpoint.executionMode);

        for (const [label, action] of [

            [
                'Load request', () => {
                    if (!supportedExecutionMode) {
                        throw new Error('This old execution mode was removed. Create a new MOCK or PROXY_FALLBACK endpoint.');
                    }
                    loadRequest(endpoint.httpMethod, '/mock/workspaces/' + workspaceId + endpoint.mockUrlPath);
                }
            ],
            [
                'Logs', async () => {
                    getPageElement('logs').textContent = JSON.stringify(await callApi('/api/endpoints/' + endpoint.endpointId + '/logs'), null, 2);
                }
            ],
            [
                'Toggle enabled', async () => {
                    await callApi('/api/endpoints/' + endpoint.endpointId + '/enabled', 'PATCH', {
                        enabled: !endpoint.enabled
                    });
                    await loadEndpoints();
                }
            ]
        ]) {
            const button = document.createElement('button');

            button.className = 'secondary';
            button.textContent = label;

            button.onclick = () => Promise.resolve().then(action).catch(showError);

            item.append(button);
        }

        getPageElement('endpoints').append(item);
    }
}

getPageElement('workspaceForm').onsubmit = async event => {
    event.preventDefault();

    try {
        const workspace = await callApi('/api/workspaces', 'POST', {
            name: getPageElement('workspaceName').value,

            description: getPageElement('description').value
        });

        await loadWorkspaces(workspace.workspaceId);

        getPageElement('notice').textContent = 'Workspace created.';
    } catch (error) {
        showError(error);
    }
};

getPageElement('endpointForm').onsubmit = async event => {
    event.preventDefault();

    try {
        if (!getPageElement('workspace').value) {
            throw new Error('Create or select a workspace first.');
        }

        await callApi('/api/endpoints', 'POST', {
            workspaceId: Number(getPageElement('workspace').value),

            endpointName: getPageElement('name').value,

            mockUrlPath: getPageElement('path').value,

            httpMethod: getPageElement('method').value,

            responseStatus: Number(getPageElement('status').value),

            responseContentType: getPageElement('type').value,

            responseBody: getPageElement('body').value,

            executionMode: getPageElement('mode').value,

            upstreamUrl: getPageElement('upstream').value || null,

            timeoutMs: Number(getPageElement('timeout').value)
        });

        await loadEndpoints();
        getPageElement('notice').textContent = 'Endpoint created. Use Load request to test it.';
    } catch (error) {
        showError(error);
    }
};

getPageElement('workspaceExample').onclick = () => loadRequest('POST', '/api/workspaces', JSON.stringify({
    name: 'API Testing',

    description: 'Requests from the tester'
}, null, 2));

getPageElement('endpointExample').onclick = () => loadRequest('POST', '/api/endpoints', JSON.stringify({
    workspaceId: Number(getPageElement('workspace').value) || 1,

    endpointName: 'Products',

    mockUrlPath: '/products',

    httpMethod: 'GET',

    responseStatus: 200,

    responseContentType: 'application/json',

    responseBody: '[{"id":1,"name":"Notebook"}]'
}, null, 2));

getPageElement('workspace').onchange = () => loadEndpoints().catch(showError);

getPageElement('refresh').onclick = () => loadEndpoints().catch(showError);

getPageElement('statsButton').onclick = () => callApi('/api/logging/stats').then(stats => {
    getPageElement('stats').textContent = JSON.stringify(stats, null, 2);
}).catch(showError);

loadWorkspaces().catch(showError);
