from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import time

# Small upstream API used only to test configured timeout fallback.
class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        print('UPSTREAM RECEIVED: GET', self.path, flush=True)
        if self.path == '/slow':
            print('LATENCY: waiting 3 seconds', flush=True)
            time.sleep(3)
        status = 500 if self.path == '/error' else 200
        body = b'{"source":"demo upstream API"}'
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        try:
            self.wfile.write(body)
            print('UPSTREAM SENT: status', status, flush=True)
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
            print('CLIENT LEFT: request timed out', flush=True)

if __name__ == '__main__':
    print('Demo upstream: http://localhost:9001 (products, slow, error)', flush=True)
    ThreadingHTTPServer(('127.0.0.1', 9001), Handler).serve_forever()
