"""Real MySQL verification using a private app and relay, never stopping the MySQL service."""
import concurrent.futures
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import socket
import subprocess
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from mysql_relay import MysqlRelay

ROOT = Path(__file__).resolve().parents[2]
RUN_ID = uuid.uuid4().hex[:8]
REPORT = {'runId': RUN_ID, 'checks': {}, 'fixtures': {}, 'cleanup': 'Verification records retained for inspection; existing demo records untouched.'}


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def require_recent_utc(value):
    instant = datetime.fromisoformat(value).replace(tzinfo=timezone.utc)
    require(abs((datetime.now(timezone.utc) - instant).total_seconds()) < 30,
            'Database timestamp is not a recent UTC value')


def wait_until(check, seconds=20):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            value = check()
            if value:
                return value
        except (OSError, ValueError):
            pass
        time.sleep(0.15)
    raise TimeoutError('Verification condition did not complete in time')


def free_port():
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', 0))
        return listener.getsockname()[1]


def configured_database():
    settings = (ROOT / 'src/main/resources/application.properties').read_text(encoding='utf-8')
    value = next(line.split('=', 1)[1] for line in settings.splitlines() if line.startswith('spring.datasource.url='))
    if value.startswith('${DB_URL:') and value.endswith('}'):
        value = os.environ.get('DB_URL') or value[len('${DB_URL:'):-1]
    parsed = urllib.parse.urlsplit(value.removeprefix('jdbc:'))
    require(parsed.scheme == 'mysql' and parsed.hostname in ('localhost', '127.0.0.1', '::1'), 'This verification supports only a local MySQL server.')
    require(parsed.username is None and parsed.password is None, 'Do not embed database credentials in the JDBC URL.')
    return parsed


class VerificationApp:
    def __init__(self, java, jar, relay, database):
        self.java = java
        self.jar = jar
        self.port = free_port()
        self.base = 'http://127.0.0.1:' + str(self.port)
        self.environment = os.environ.copy()
        url = urllib.parse.urlunsplit(('mysql', '127.0.0.1:' + str(relay.port), database.path, database.query, ''))
        self.environment['DB_URL'] = 'jdbc:' + url
        self.process = None
        self.output = None
        self.start_number = 0

    def start(self, upstream_origin):
        self.start_number += 1
        self.environment['PROXY_ALLOWED_ORIGINS'] = upstream_origin
        path = ROOT / 'logs' / ('mysql-verification-' + RUN_ID + '-' + str(self.start_number) + '.log')
        self.log_path = path
        self.output = path.open('w', encoding='utf-8')
        command = [str(self.java), '-Djava.rmi.server.hostname=127.0.0.1', '-jar', str(self.jar),
                   '--server.address=127.0.0.1', '--server.port=' + str(self.port),
                   '--spring.application.admin.enabled=true', '--spring.jmx.enabled=true',
                   '--spring.datasource.hikari.connection-timeout=1000',
                   '--spring.datasource.hikari.maximum-pool-size=3',
                   '--spring.datasource.hikari.data-source-properties.connectTimeout=1000',
                   '--spring.datasource.hikari.data-source-properties.socketTimeout=10000',
                   '--mockforge.logging.capacity=30', '--mockforge.logging.workers=1']
        self.process = subprocess.Popen(command, cwd=ROOT, env=self.environment, stdout=self.output, stderr=subprocess.STDOUT)
        wait_until(lambda: self.call('GET', '/api/workspaces')[0] == 200, 35)

    def call(self, method, path, body=None):
        data = None if body is None else json.dumps(body).encode()
        request = urllib.request.Request(self.base + path, data=data, method=method, headers={'Content-Type': 'application/json'})
        try:
            response = urllib.request.urlopen(request, timeout=10)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            text = response.read().decode()
            return response.status, json.loads(text) if text else None

    def shutdown(self, before_drain=None):
        start = time.monotonic()
        helper = subprocess.Popen([str(self.java), '--add-modules', 'jdk.attach',
                                   str(ROOT / 'tools/verification/ShutdownApp.java'), str(self.process.pid)],
                                  stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        # Release the relay only after the graceful shutdown call is about to be made.
        if before_drain:
            for line in helper.stdout:
                if 'SHUTDOWN_REQUESTED' in line:
                    time.sleep(0.15)
                    before_drain()
                    break
        output, _ = helper.communicate(timeout=20)
        require(helper.returncode == 0, 'Graceful shutdown helper failed: ' + output)
        self.process.wait(timeout=15)
        require(self.process.returncode == 0, 'Verification app did not stop cleanly')
        self.output.close()
        shutdown_log = self.log_path.read_text(encoding='utf-8')
        require('Application shutdown requested.' in shutdown_log and 'Graceful shutdown complete' in shutdown_log,
                'Missing graceful shutdown evidence in verification log')
        self.process = None
        return round(time.monotonic() - start, 3)

    def emergency_stop(self):
        if self.process and self.process.poll() is None:
            self.process.terminate()
            self.process.wait(timeout=10)
        if self.output and not self.output.closed:
            self.output.close()


def verify():
    java_home = Path(os.environ.get('JAVA_HOME', r'C:\Program Files\Java\jdk-27'))
    java = java_home / 'bin/java.exe'
    jar = ROOT / 'target/mockforge-cloud-0.0.1-SNAPSHOT.jar'
    require(java.exists() and jar.exists(), 'Build the application package with JDK 27 before verification.')
    database = configured_database()
    relay = MysqlRelay(database.hostname, database.port or 3306)
    started = threading.Condition()
    release = threading.Event()
    counts = {'started': 0}

    class Upstream(BaseHTTPRequestHandler):
        def do_GET(self):
            with started:
                counts['started'] += 1
                started.notify_all()
            release.wait(10)
            data = b'{"ok":true}'
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(data)))
            self.end_headers()
            try:
                self.wfile.write(data)
            except OSError:
                pass

        def log_message(self, *_):
            pass

    upstream = ThreadingHTTPServer(('127.0.0.1', 0), Upstream)
    upstream.daemon_threads = True
    upstream_thread = threading.Thread(target=upstream.serve_forever, daemon=True)
    upstream_thread.start()
    origin = 'http://127.0.0.1:' + str(upstream.server_port)
    app = VerificationApp(java, jar, relay, database)
    pool = concurrent.futures.ThreadPoolExecutor(max_workers=20)
    try:
        app.start(origin)
        status, workspace = app.call('POST', '/api/workspaces', {'name': 'Verification ' + RUN_ID, 'description': 'Isolated MySQL relay verification'})
        require(status == 201, 'Workspace creation failed')
        require_recent_utc(workspace['createdAt'])
        wid = workspace['workspaceId']
        REPORT['fixtures']['workspaceId'] = wid

        def create(route, mode='MOCK'):
            body = {'name': route, 'routePath': route, 'httpMethod': 'POST', 'responseStatus': 200,
                    'responseContentType': 'application/json', 'responseBody': '{"ok":true}', 'executionMode': mode}
            if mode != 'MOCK':
                body.update(httpMethod='GET', upstreamUrl=origin + '/delay', timeoutMs=10000)
            status, endpoint = app.call('POST', '/api/workspaces/' + str(wid) + '/endpoints', body)
            require(status == 201, 'Document-compatible creation failed')
            require_recent_utc(endpoint['createdAt'])
            return endpoint

        endpoint = create('/verify')
        proxy = create('/delayed', 'PROXY_FALLBACK')
        REPORT['fixtures'].update(endpointId=endpoint['endpointId'], proxyEndpointId=proxy['endpointId'])
        route = '/mock/workspaces/' + str(wid) + '/verify'
        log_path = '/api/endpoints/' + str(endpoint['endpointId']) + '/logs?limit=100'
        status, _ = app.call('POST', route, {'productId': 42, 'quantity': 2, 'password': 'verification-secret', 'email': 'private@example.invalid'})
        require(status == 200, 'Mock response failed')
        logs = wait_until(lambda: app.call('GET', log_path)[1])
        require_recent_utc(logs[0]['receivedAt'])
        REPORT['checks']['mysqlCreatedAndReceivedTimesUseUtc'] = True
        require(json.loads(logs[0]['requestBodySample']) == {'productId': 42, 'quantity': 2}, 'Body sampling did not omit unapproved fields')
        REPORT['checks']['allowlistedSamplePersistedInMysql'] = True

        futures = [pool.submit(app.call, 'POST', route, {'productId': index, 'quantity': 1}) for index in range(20)]
        require(all(future.result()[0] == 200 for future in futures), 'Concurrent mock request failed')
        wait_until(lambda: len(app.call('GET', log_path)[1]) == 21)
        REPORT['checks']['twentyConcurrentRequestsAndLogs'] = True

        # Each proxy request finishes its DB lookup before it reaches this controlled upstream.
        delayed_path = '/mock/workspaces/' + str(wid) + '/delayed'
        futures = [pool.submit(app.call, 'GET', delayed_path) for _ in range(6)]
        wait_until(lambda: counts['started'] >= 6)
        relay.forwarding.clear()
        release.set()
        require(all(future.result()[0] == 200 for future in futures), 'Delayed response failed')
        stats = app.call('GET', '/api/logging/stats')[1]
        require(stats['queued'] > 0, 'No queued writes were observed for the shutdown test')
        REPORT['checks']['queuedBeforeGracefulShutdown'] = stats['queued']
        REPORT['checks']['gracefulShutdownSeconds'] = app.shutdown(relay.forwarding.set)

        app.start(origin)
        require(len(app.call('GET', log_path)[1]) == 21, 'Request logs did not survive restart')
        proxy_log_path = '/api/endpoints/' + str(proxy['endpointId']) + '/logs?limit=100'
        require(len(app.call('GET', proxy_log_path)[1]) == 6, 'Queued logs did not drain before shutdown')
        REPORT['checks']['queuedLogsDrainedAndPersistedAfterRestart'] = True

        # Drop only this app's relay after its endpoint lookup but before it enqueues the log.
        release.clear()
        before = counts['started']
        future = pool.submit(app.call, 'GET', delayed_path)
        wait_until(lambda: counts['started'] > before)
        relay.disconnect()
        release.set()
        require(future.result()[0] == 200, 'Upstream response was blocked by a log-write failure')
        wait_until(lambda: app.call('GET', '/api/logging/stats')[1]['failed'] >= 1, 20)
        status, error = app.call('GET', '/api/workspaces')
        require(status == 500 and error.get('error') == 'Internal server error' and 'reference' in error, 'Database outage was not surfaced as a safe server error')
        REPORT['checks']['databaseDisconnectAndWriterFailureCounted'] = True

        relay.reconnect()
        wait_until(lambda: app.call('GET', '/api/workspaces')[0] == 200)
        require(app.call('POST', route, {'productId': 77, 'quantity': 1})[0] == 200, 'Recovery request failed')
        wait_until(lambda: len(app.call('GET', log_path)[1]) == 22)
        require(len(app.call('GET', proxy_log_path)[1]) == 6, 'Failed best-effort log unexpectedly appeared')
        REPORT['checks']['connectionRecoveredAndNewLogsPersisted'] = True
        REPORT['checks']['finalShutdownSeconds'] = app.shutdown()
        REPORT['result'] = 'PASS'
    finally:
        release.set()
        relay.forwarding.set()
        app.emergency_stop()
        pool.shutdown(wait=True)
        upstream.shutdown()
        upstream.server_close()
        relay.close()


if __name__ == '__main__':
    (ROOT / 'logs').mkdir(exist_ok=True)
    try:
        verify()
    except Exception as error:
        REPORT['result'] = 'FAIL'
        REPORT['error'] = str(error)
        raise
    finally:
        destination = ROOT / 'logs/mysql-verification-report.json'
        destination.write_text(json.dumps(REPORT, indent=2), encoding='utf-8')
        print(json.dumps(REPORT, indent=2))
