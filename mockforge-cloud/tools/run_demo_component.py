"""Run one demo server and show its output in a visible console and log file."""
import os
from pathlib import Path
import socket
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
LOGS = ROOT / 'logs'
LOGS.mkdir(exist_ok=True)


def port_open(port):
    try:
        with socket.create_connection(('127.0.0.1', port), timeout=1):
            return True
    except OSError:
        return False


def watch(path):
    print('Showing live output from:', path)
    if not path.exists():
        print('The server is already running elsewhere. No log file is available here.')
        return
    with path.open(encoding='utf-8', errors='replace') as stream:
        lines = stream.readlines()
        print(''.join(lines[-25:]), end='')
        while True:
            line = stream.readline()
            if line:
                print(line, end='', flush=True)
            else:
                time.sleep(0.3)


def run(component):
    port = 8080 if component == 'backend' else 9001
    logfile = LOGS / (component + '.log')
    if port_open(port):
        print('Port', port, 'is already running. Not starting a duplicate server.')
        watch(logfile)
        return

    environment = os.environ.copy()
    if component == 'backend':
        print('BACKEND + FRONTEND: http://localhost:8080/')
        print('Watch for REQUEST, UPSTREAM, RESPONSE and LOG SAVED messages.\n')
        jdk = Path(r'C:\Program Files\Java\jdk-27')
        if not environment.get('JAVA_HOME') and jdk.exists():
            environment['JAVA_HOME'] = str(jdk)
        if environment.get('JAVA_HOME'):
            environment['PATH'] = environment['JAVA_HOME'] + r'\bin;' + environment.get('PATH', '')
        environment.setdefault('PROXY_ALLOWED_ORIGINS', 'http://localhost:9001,https://fakestoreapi.com')
        command = ['cmd.exe', '/d', '/c', 'mvnw.cmd', '-q', 'spring-boot:run']
    else:
        print('UPSTREAM TEST API: http://localhost:9001')
        print('/slow waits three seconds; MockForge gives up after 1.5 seconds.\n')
        command = [sys.executable, '-u', str(ROOT / 'tools' / 'demo_upstream.py')]

    process = subprocess.Popen(command, cwd=ROOT, env=environment,
                               stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                               text=True, encoding='utf-8', errors='replace')
    try:
        with logfile.open('w', encoding='utf-8') as output:
            for line in process.stdout:
                print(line, end='', flush=True)
                output.write(line)
                output.flush()
        if process.wait() != 0:
            print('Server stopped with exit code', process.returncode)
    except KeyboardInterrupt:
        print('\nStopping this demo component...')
        # The console's Ctrl+C is also delivered to the child server.
        try:
            process.wait(timeout=8)
        except subprocess.TimeoutExpired:
            process.terminate()


if __name__ == '__main__':
    try:
        run(sys.argv[1])
    except KeyboardInterrupt:
        print('\nLog viewer stopped.')
