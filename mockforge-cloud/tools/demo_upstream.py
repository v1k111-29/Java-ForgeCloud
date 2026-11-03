from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import time

# Small upstream API used only to test configured timeout fallback.
class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        print('UPSTREAM RECEIVED: GET', self.path, flush=True)
        if self.path == '/slow':
            print('LATENCY: waiting 3 seconds', flush=True)
            time.sleep(3)
        status = 500 if self.path == '/error' else 200
        products = [
            {'id': 101, 'name': 'Upstream Notebook', 'price': 7.00},
            {'id': 102, 'name': 'Upstream Headphones', 'price': 45.00},
            {'id': 103, 'name': 'Upstream Backpack', 'price': 35.00},
            {'id': 104, 'name': 'Upstream Travel Mug', 'price': 13.50}
        ]
        data = {'error': 'Demo upstream intentionally returned HTTP 500'} if status == 500 else products
        body = json.dumps(data).encode('utf-8')
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        try:
            self.wfile.write(body)
            print('UPSTREAM SENT: status', status, flush=True)
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
            print('CLIENT LEFT: request timed out', flush=True)

if __name__ == '__main__':
    print('Demo upstream: http://localhost:9001 (products, slow, error)', flush=True)
    ThreadingHTTPServer(('127.0.0.1', 9001), Handler).serve_forever()
