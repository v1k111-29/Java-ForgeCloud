"""A loopback-only relay used to interrupt only the verification app's DB connection."""
import socket
import threading


class MysqlRelay:
    def __init__(self, host, port):
        self.target = (host, port)
        self.available = True
        self.running = True
        self.lock = threading.Lock()
        self.connections = set()
        self.forwarding = threading.Event()
        self.forwarding.set()
        self.listener = socket.socket()
        self.listener.bind(('127.0.0.1', 0))
        self.listener.listen()
        self.listener.settimeout(0.2)
        self.port = self.listener.getsockname()[1]
        self.thread = threading.Thread(target=self.accept, daemon=True)
        self.thread.start()

    def accept(self):
        while self.running:
            try:
                client, _ = self.listener.accept()
            except socket.timeout:
                continue
            except OSError:
                break
            with self.lock:
                if not self.available:
                    client.close()
                    continue
                try:
                    upstream = socket.create_connection(self.target, timeout=2)
                    upstream.settimeout(None)
                except OSError:
                    client.close()
                    continue
                self.connections.update((client, upstream))
            threading.Thread(target=self.pump, args=(client, upstream), daemon=True).start()
            threading.Thread(target=self.pump, args=(upstream, client), daemon=True).start()

    def pump(self, source, destination):
        try:
            while self.running:
                chunk = source.recv(65536)
                if not chunk:
                    break
                self.forwarding.wait()
                destination.sendall(chunk)
        except OSError:
            pass
        finally:
            with self.lock:
                self.connections.discard(source)
                self.connections.discard(destination)
            for connection in (source, destination):
                try:
                    connection.shutdown(socket.SHUT_RDWR)
                except OSError:
                    pass
                connection.close()

    def disconnect(self):
        self.forwarding.set()
        with self.lock:
            self.available = False
            connections = list(self.connections)
        for connection in connections:
            try:
                connection.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
            connection.close()

    def reconnect(self):
        with self.lock:
            self.available = True

    def close(self):
        self.running = False
        self.disconnect()
        self.listener.close()
        self.thread.join(timeout=2)
