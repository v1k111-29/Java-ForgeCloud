const element = id => document.getElementById(id);
const history = [];

async function api(path, method = 'GET', data) {
    const response = await fetch(path, {
        method,
        headers: {'Content-Type': 'application/json'},
        body: data === undefined ? undefined : JSON.stringify(data)
    });
    const text = await response.text();
    if (!response.ok) throw new Error(text);
    return text ? JSON.parse(text) : null;
}

function showError(error) {
    element('notice').textContent = error.message;
}

function loadRequest(method, path, body = '') {
    element('requestMethod').value = method;
    element('requestPath').value = path;
    element('requestBody').value = body;
    element('notice').textContent = 'Request loaded. Edit it if needed, then click Send request.';
}

function renderHistory() {
    element('history').replaceChildren();
    for (const request of history) {
        const item = document.createElement('li');
        const button = document.createElement('button');
        button.className = 'secondary';
        button.textContent = request.method + ' ' + request.path + ' → ' + request.status;
        button.onclick = () => loadRequest(request.method, request.path, request.body);
        item.append(button);
        element('history').append(item);
    }
}

// Send real HTTP requests to this application's API and mock routes.
element('requestForm').onsubmit = async event => {
    event.preventDefault();
    element('send').disabled = true;
    element('notice').textContent = '';
    element('responseStatus').textContent = 'Sending…';
    element('responseBody').textContent = '';
    element('responseHeaders').textContent = '';
    element('responseTime').textContent = '';
    element('responseSize').textContent = '';
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 15000);
    try {
        const method = element('requestMethod').value;
        const input = element('requestPath').value.trim();
        const url = new URL(input, window.location.origin);
        if (url.origin !== window.location.origin || !/^\/(api|mock)(\/|$)/.test(url.pathname)) {
            throw new Error('Use an /api or /mock URL on this MockForge server.');
        }
        const headers = JSON.parse(element('requestHeaders').value || '{}');
        if (!headers || Array.isArray(headers) || typeof headers !== 'object') throw new Error('Headers must be a JSON object.');
        for (const value of Object.values(headers)) {
            if (typeof value !== 'string') throw new Error('Header values must be strings.');
        }
        const body = element('requestBody').value;
        const start = performance.now();
        const response = await fetch(url, {
            method, headers, signal: controller.signal, cache: 'no-store',
            body: method === 'GET' || method === 'HEAD' || !body ? undefined : body
        });
        const text = await response.text();
        const duration = Math.round(performance.now() - start);
        element('responseStatus').textContent = response.status + ' ' + response.statusText;
        element('responseTime').textContent = duration + ' ms';
        element('responseSize').textContent = new TextEncoder().encode(text).length + ' bytes';
        try { element('responseBody').textContent = JSON.stringify(JSON.parse(text), null, 2); }
        catch (error) { element('responseBody').textContent = text || '(empty response body)'; }
        element('responseHeaders').textContent = Array.from(response.headers, ([key, value]) => key + ': ' + value).join('\n');
        history.unshift({method, path: url.pathname + url.search, body, status: response.status});
        if (history.length > 10) history.pop();
        renderHistory();
        if (method !== 'GET' && method !== 'HEAD') await workspaces(element('workspace').value);
    } catch (error) {
        element('responseStatus').textContent = 'Request not completed';
        showError(error.name === 'AbortError' ? new Error('Request exceeded the 15-second client timeout.') : error);
    } finally {
        clearTimeout(timer);
        element('send').disabled = false;
    }
};

async function workspaces(selected) {
    const rows = await api('/api/workspaces');
    element('workspace').replaceChildren();
    for (const workspace of rows) element('workspace').add(new Option(workspace.name, workspace.workspaceId));
    if (selected && rows.some(workspace => String(workspace.workspaceId) === String(selected))) element('workspace').value = selected;
    await endpoints();
}

async function endpoints() {
    element('endpoints').replaceChildren();
    const workspaceId = element('workspace').value;
    if (!workspaceId) return;
    for (const endpoint of await api('/api/endpoints/workspace/' + workspaceId)) {
        const item = document.createElement('li');
        const title = document.createElement('div');
        title.textContent = endpoint.httpMethod + ' ' + endpoint.mockUrlPath + ' · ' + endpoint.executionMode + ' · ' + (endpoint.enabled ? 'enabled' : 'disabled');
        item.append(title);
        const supported = ['MOCK', 'PROXY_FALLBACK'].includes(endpoint.executionMode);
        for (const [label, action] of [
            ['Load request', () => { if (!supported) throw new Error('This old execution mode was removed. Create a new MOCK or PROXY_FALLBACK endpoint.'); loadRequest(endpoint.httpMethod, '/mock/workspaces/' + workspaceId + endpoint.mockUrlPath); }],
            ['Logs', async () => { element('logs').textContent = JSON.stringify(await api('/api/endpoints/' + endpoint.endpointId + '/logs'), null, 2); }],
            ['Toggle enabled', async () => { await api('/api/endpoints/' + endpoint.endpointId + '/enabled', 'PATCH', {enabled: !endpoint.enabled}); await endpoints(); }]
        ]) {
            const button = document.createElement('button');
            button.className = 'secondary'; button.textContent = label;
            button.onclick = () => Promise.resolve().then(action).catch(showError);
            item.append(button);
        }
        element('endpoints').append(item);
    }
}

element('workspaceForm').onsubmit = async event => {
    event.preventDefault();
    try {
        const workspace = await api('/api/workspaces', 'POST', {name: element('workspaceName').value, description: element('description').value});
        await workspaces(workspace.workspaceId);
        element('notice').textContent = 'Workspace created.';
    } catch (error) { showError(error); }
};
element('endpointForm').onsubmit = async event => {
    event.preventDefault();
    try {
        if (!element('workspace').value) throw new Error('Create or select a workspace first.');
        await api('/api/endpoints', 'POST', {
            workspaceId: Number(element('workspace').value), endpointName: element('name').value,
            mockUrlPath: element('path').value, httpMethod: element('method').value,
            responseStatus: Number(element('status').value), responseContentType: element('type').value,
            responseBody: element('body').value, executionMode: element('mode').value,
            upstreamUrl: element('upstream').value || null, timeoutMs: Number(element('timeout').value)
        });
        await endpoints(); element('notice').textContent = 'Endpoint created. Use Load request to test it.';
    } catch (error) { showError(error); }
};
element('workspaceExample').onclick = () => loadRequest('POST', '/api/workspaces', JSON.stringify({name: 'API Testing', description: 'Requests from the tester'}, null, 2));
element('endpointExample').onclick = () => loadRequest('POST', '/api/endpoints', JSON.stringify({workspaceId: Number(element('workspace').value) || 1, endpointName: 'Products', mockUrlPath: '/products', httpMethod: 'GET', responseStatus: 200, responseContentType: 'application/json', responseBody: '[{"id":1,"name":"Notebook"}]'}, null, 2));
element('workspace').onchange = () => endpoints().catch(showError);
element('refresh').onclick = () => endpoints().catch(showError);
element('statsButton').onclick = () => api('/api/logging/stats').then(stats => { element('stats').textContent = JSON.stringify(stats, null, 2); }).catch(showError);
workspaces().catch(showError);
