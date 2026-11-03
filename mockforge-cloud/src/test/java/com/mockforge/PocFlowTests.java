package com.mockforge;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;
import com.sun.net.httpserver.HttpServer;
import com.mockforge.service.AsyncRequestLogger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:poc;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password=", "mockforge.proxy.allowed-origins=http://localhost:18991"
})
class PocFlowTests {
    @Autowired Environment environment;
    @Autowired JdbcTemplate jdbc;
    @Autowired AsyncRequestLogger logger;
    final HttpClient client = HttpClient.newHttpClient();
    HttpServer upstream;
    ExecutorService serverWorkers;

    @BeforeEach void setup() throws Exception {
        jdbc.execute("CREATE TABLE IF NOT EXISTS workspaces (workspace_id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(120), description VARCHAR(500), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS endpoints (endpoint_id BIGINT AUTO_INCREMENT PRIMARY KEY, workspace_id BIGINT REFERENCES workspaces(workspace_id), name VARCHAR(120), http_method VARCHAR(10), route_path VARCHAR(255), response_status INT, response_content_type VARCHAR(120), response_body TEXT, enabled BOOLEAN DEFAULT TRUE, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, execution_mode VARCHAR(20) DEFAULT 'MOCK', upstream_url VARCHAR(2000), timeout_ms INT DEFAULT 1500, UNIQUE(workspace_id, http_method, route_path))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS request_logs (log_id BIGINT AUTO_INCREMENT PRIMARY KEY, endpoint_id BIGINT REFERENCES endpoints(endpoint_id), received_at TIMESTAMP, http_method VARCHAR(10), request_path VARCHAR(500), response_status INT, duration_ms INT, request_body_sample TEXT, response_source VARCHAR(30), fallback_reason VARCHAR(120))");
        upstream = HttpServer.create(new InetSocketAddress("localhost", 18991), 0);
        serverWorkers = Executors.newFixedThreadPool(2);
        upstream.setExecutor(serverWorkers);
        upstream.createContext("/slow", exchange -> {
            try { Thread.sleep(500); exchange.sendResponseHeaders(200, 2); exchange.getResponseBody().write("ok".getBytes()); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        upstream.createContext("/error", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.sendResponseHeaders(500, 4); exchange.getResponseBody().write("oops".getBytes()); exchange.close();
        });
        upstream.start();
    }
    @AfterEach void cleanup() { upstream.stop(0); serverWorkers.shutdownNow(); }
    HttpResponse<String> call(String method, String path, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + environment.getProperty("local.server.port") + path))
                .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
    long id(String json, String field) {
        var matcher = java.util.regex.Pattern.compile("\"" + field + "\"\\s*:\\s*(\\d+)").matcher(json);
        assertTrue(matcher.find(), json); return Long.parseLong(matcher.group(1));
    }
    @Test void completeFlow() throws Exception {
        assertEquals(400, call("POST", "/api/workspaces", "{\"name\":\" \"}").statusCode());
        var workspace = call("POST", "/api/workspaces", "{\"name\":\"Demo\"}");
        assertEquals(201, workspace.statusCode());
        long wid = id(workspace.body(), "workspaceId");
        String body = "{\"workspaceId\":" + wid + ",\"endpointName\":\"Products\",\"mockUrlPath\":\"/products\",\"httpMethod\":\"GET\",\"responseStatus\":201,\"responseContentType\":\"text/plain\",\"responseBody\":\"sample\"}";
        var created = call("POST", "/api/endpoints", body);
        assertEquals(201, created.statusCode(), created.body());
        long eid = id(created.body(), "endpointId");
        assertEquals(404, call("POST", "/api/endpoints", body.replace("\"workspaceId\":" + wid, "\"workspaceId\":999999")).statusCode());
        assertEquals(400, call("POST", "/api/endpoints", body.replace("\"responseStatus\":201", "\"responseStatus\":900")).statusCode());
        assertEquals(400, call("POST", "/api/endpoints", body.replace("\"responseBody\":\"sample\"", "\"responseBody\":\"sample\",\"executionMode\":\"PROXY_FALLBACK\",\"upstreamUrl\":\"http://unapproved.invalid/products\"")).statusCode());
        assertTrue(call("GET", "/", "").body().contains("MockForge Cloud"));
        assertEquals(400, call("POST", "/api/endpoints", body.replace("\"responseBody\":\"sample\"", "\"responseBody\":\"sample\",\"executionMode\":\"PROXY_CACHE\"")).statusCode());
        assertEquals(409, call("POST", "/api/endpoints", body).statusCode());
        var mock = call("GET", "/mock/workspaces/" + wid + "/products", "");
        assertEquals(201, mock.statusCode()); assertEquals("sample", mock.body());
        assertEquals(404, call("POST", "/mock/workspaces/" + wid + "/products", "").statusCode());
        assertEquals(404, call("GET", "/mock/workspaces/999999/products", "").statusCode());
        var calls = new java.util.ArrayList<CompletableFuture<Void>>();
        for (int i = 0; i < 20; i++) calls.add(CompletableFuture.runAsync(() -> {
            try { assertEquals("sample", call("GET", "/mock/workspaces/" + wid + "/products", "").body()); }
            catch (Exception ex) { throw new RuntimeException(ex); }
        }));
        CompletableFuture.allOf(calls.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (jdbc.queryForObject("SELECT COUNT(*) FROM request_logs WHERE endpoint_id = ?", Long.class, eid) < 21 && System.nanoTime() < deadline) Thread.sleep(20);
        assertEquals(21L, jdbc.queryForObject("SELECT COUNT(*) FROM request_logs WHERE endpoint_id = ?", Long.class, eid));
        assertTrue(call("GET", "/api/endpoints/" + eid + "/logs", "").body().contains("MOCK"));
        assertEquals(200, call("PATCH", "/api/endpoints/" + eid + "/enabled", "{\"enabled\":false}").statusCode());
        assertEquals(404, call("GET", "/mock/workspaces/" + wid + "/products", "").statusCode());
    }
    @Test void timeoutFallsBackButHttpErrorsPassThrough() throws Exception {
        long wid = id(call("POST", "/api/workspaces", "{\"name\":\"Proxy\"}").body(), "workspaceId");
        for (String path : new String[]{"slow", "error"}) {
            String body = "{\"workspaceId\":" + wid + ",\"endpointName\":\"Proxy\",\"mockUrlPath\":\"/" + path + "\",\"httpMethod\":\"GET\",\"responseStatus\":200,\"responseContentType\":\"text/plain\",\"responseBody\":\"fallback\",\"executionMode\":\"PROXY_FALLBACK\",\"timeoutMs\":100,\"upstreamUrl\":\"http://localhost:18991/" + path + "\"}";
            assertEquals(201, call("POST", "/api/endpoints", body).statusCode());
            var response = call("GET", "/mock/workspaces/" + wid + "/" + path, "");
            assertEquals(path.equals("slow") ? 200 : 500, response.statusCode());
            assertEquals(path.equals("slow") ? "fallback" : "oops", response.body());
        }
    }

}
