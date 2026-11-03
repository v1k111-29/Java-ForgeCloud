package com.mockforge;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import com.mockforge.service.AsyncRequestLogger;
import com.sun.net.httpserver.HttpServer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {

    "spring.datasource.url=jdbc:h2:mem:poc;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password=", "mockforge.proxy.allowed-origins=http://localhost:18991"
})
class PocFlowTests {

    @Autowired Environment environment;

    @Autowired JdbcTemplate jdbcTemplate;

    @Autowired AsyncRequestLogger asyncRequestLogger;

    final HttpClient httpClient = HttpClient.newHttpClient();

    HttpServer upstreamServer;

    ExecutorService upstreamServerWorkers;

    @BeforeEach
    void setUpTestDatabaseAndUpstream() throws Exception {

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS workspaces (workspace_id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                + "name VARCHAR(120), description VARCHAR(500), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS endpoints (endpoint_id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                + "workspace_id BIGINT REFERENCES workspaces(workspace_id), name VARCHAR(120), "
                + "http_method VARCHAR(10), route_path VARCHAR(255), response_status INT, "
                + "response_content_type VARCHAR(120), response_body TEXT, enabled BOOLEAN DEFAULT TRUE, "
                + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, execution_mode VARCHAR(20) DEFAULT 'MOCK', "
                + "upstream_url VARCHAR(2000), timeout_ms INT DEFAULT 1500, UNIQUE(workspace_id, http_method, "
                + "route_path))");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS request_logs (log_id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                + "endpoint_id BIGINT REFERENCES endpoints(endpoint_id), received_at TIMESTAMP, "
                + "http_method VARCHAR(10), request_path VARCHAR(500), response_status INT, duration_ms INT, "
                + "request_body_sample TEXT, response_source VARCHAR(30), fallback_reason VARCHAR(120))");

        upstreamServer = HttpServer.create(new InetSocketAddress("localhost", 18991), 0);

        upstreamServerWorkers = Executors.newFixedThreadPool(2);

        upstreamServer.setExecutor(upstreamServerWorkers);

        upstreamServer.createContext("/slow", httpExchange -> {

            try {
                Thread.sleep(500);
                httpExchange.sendResponseHeaders(200, 2);
                httpExchange.getResponseBody().write("ok".getBytes());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                httpExchange.close();
            }
        });

        upstreamServer.createContext("/error", httpExchange -> {

            httpExchange.getResponseHeaders().add("Content-Type", "text/plain");

            httpExchange.sendResponseHeaders(500, 4);
            httpExchange.getResponseBody().write("oops".getBytes());
            httpExchange.close();
        });

        upstreamServer.start();
    }


    @AfterEach
    void stopTestUpstream() {
        upstreamServer.stop(0);
        upstreamServerWorkers.shutdownNow();
    }

    HttpResponse<String> sendHttpRequest(String httpMethod, String requestPath, String requestBody) throws Exception {

        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create("http://localhost:" + environment.getProperty("local.server.port") + requestPath))
            .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
            .method(httpMethod, HttpRequest.BodyPublishers.ofString(requestBody)).build();

        return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    }

    long readIdFromJson(String jsonResponse, String fieldName) {

        Matcher idMatcher = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*(\\d+)").matcher(jsonResponse);

        assertTrue(idMatcher.find(), jsonResponse);
        return Long.parseLong(idMatcher.group(1));
    }


    @Test
    void workspaceEndpointAndMockFlowWorks() throws Exception {

        assertEquals(400, sendHttpRequest("POST", "/api/workspaces", "{\"name\":\" \"}").statusCode());

        HttpResponse<String> workspaceResponse = sendHttpRequest("POST", "/api/workspaces", "{\"name\":\"Demo\"}");

        assertEquals(201, workspaceResponse.statusCode());

        long workspaceId = readIdFromJson(workspaceResponse.body(), "workspaceId");

        String requestBody = "{\"workspaceId\":" + workspaceId + ",\"endpointName\":\"Products\",\"mockUrlPath\":\"/products\",\"httpMethod\":\"GET\",\"responseStatus\":201,\"responseContentType\":\"text/plain\",\"responseBody\":\"sample\"}";

        HttpResponse<String> createdEndpointResponse = sendHttpRequest("POST", "/api/endpoints", requestBody);

        assertEquals(201, createdEndpointResponse.statusCode(), createdEndpointResponse.body());

        long endpointId = readIdFromJson(createdEndpointResponse.body(), "endpointId");

        assertEquals(404, sendHttpRequest("POST", "/api/endpoints", requestBody.replace("\"workspaceId\":" + workspaceId, "\"workspaceId\":999999")).statusCode());

        assertEquals(400, sendHttpRequest("POST", "/api/endpoints", requestBody.replace("\"responseStatus\":201", "\"responseStatus\":900")).statusCode());

        assertEquals(400, sendHttpRequest("POST", "/api/endpoints", requestBody.replace("\"responseBody\":\"sample\"", "\"responseBody\":\"sample\",\"executionMode\":\"PROXY_FALLBACK\",\"upstreamUrl\":\"http://unapproved.invalid/products\"")).statusCode());

        assertTrue(sendHttpRequest("GET", "/", "").body().contains("MockForge Cloud"));

        assertEquals(400, sendHttpRequest("POST", "/api/endpoints", requestBody.replace("\"responseBody\":\"sample\"", "\"responseBody\":\"sample\",\"executionMode\":\"PROXY_CACHE\"")).statusCode());

        assertEquals(409, sendHttpRequest("POST", "/api/endpoints", requestBody).statusCode());

        HttpResponse<String> mockResponse = sendHttpRequest("GET", "/mock/workspaces/" + workspaceId + "/products", "");

        assertEquals(201, mockResponse.statusCode());
        assertEquals("sample", mockResponse.body());

        assertEquals(404, sendHttpRequest("POST", "/mock/workspaces/" + workspaceId + "/products", "").statusCode());

        assertEquals(404, sendHttpRequest("GET", "/mock/workspaces/" + workspaceId + "/Products", "").statusCode());

        assertEquals(404, sendHttpRequest("GET", "/mock/workspaces/999999/products", "").statusCode());

        ArrayList<CompletableFuture<Void>> concurrentRequests = new ArrayList<CompletableFuture<Void>>();

        for (int requestNumber = 0; requestNumber < 20; requestNumber++) concurrentRequests.add(CompletableFuture.runAsync(() -> {

            try {
                assertEquals("sample", sendHttpRequest("GET", "/mock/workspaces/" + workspaceId + "/products", "").body());
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }));

        CompletableFuture.allOf(concurrentRequests.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);

        long loggingDeadlineNanoseconds = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);

        while (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM request_logs WHERE endpoint_id = ?", Long.class, endpointId) < 21 && System.nanoTime() < loggingDeadlineNanoseconds) Thread.sleep(20);

        assertEquals(21L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM request_logs WHERE endpoint_id = ?", Long.class, endpointId));

        assertTrue(sendHttpRequest("GET", "/api/endpoints/" + endpointId + "/logs", "").body().contains("MOCK"));

        assertEquals(200, sendHttpRequest("PATCH", "/api/endpoints/" + endpointId + "/enabled", "{\"enabled\":false}").statusCode());

        assertEquals(404, sendHttpRequest("GET", "/mock/workspaces/" + workspaceId + "/products", "").statusCode());
    }


    @Test
    void timeoutFallsBackButHttpErrorsPassThrough() throws Exception {

        long workspaceId = readIdFromJson(sendHttpRequest("POST", "/api/workspaces", "{\"name\":\"Proxy\"}").body(), "workspaceId");

        for (String requestPath : new String[]{
            "slow", "error"
        }) {

            String requestBody = "{\"workspaceId\":" + workspaceId + ",\"endpointName\":\"Proxy\",\"mockUrlPath\":\"/" + requestPath + "\",\"httpMethod\":\"GET\",\"responseStatus\":200,\"responseContentType\":\"text/plain\",\"responseBody\":\"fallback\",\"executionMode\":\"PROXY_FALLBACK\",\"timeoutMs\":100,\"upstreamUrl\":\"http://localhost:18991/" + requestPath + "\"}";

            assertEquals(201, sendHttpRequest("POST", "/api/endpoints", requestBody).statusCode());

            HttpResponse<String> httpResponse = sendHttpRequest("GET", "/mock/workspaces/" + workspaceId + "/" + requestPath, "");

            assertEquals(requestPath.equals("slow") ? 200 : 500, httpResponse.statusCode());

            assertEquals(requestPath.equals("slow") ? "fallback" : "oops", httpResponse.body());
        }
    }


    @Test
    void documentApiAndLegacyApiBothWorkAndSamplesOmitSecrets() throws Exception {

        long workspaceId = readIdFromJson(sendHttpRequest("POST", "/api/workspaces", "{\"name\":\"Document API\"}").body(), "workspaceId");

        String requestBody = "{\"name\":\"Order preview\",\"routePath\":\"/orders\",\"httpMethod\":\"POST\","
            + "\"responseStatus\":201,\"responseContentType\":\"application/json\",\"responseBody\":\"{\\\"ok\\\":true}\"}";

        String createEndpointPath = "/api/workspaces/" + workspaceId + "/endpoints";

        HttpResponse<String> createdEndpointResponse = sendHttpRequest("POST", createEndpointPath, requestBody);

        assertEquals(201, createdEndpointResponse.statusCode(), createdEndpointResponse.body());

        assertTrue(createdEndpointResponse.body().contains("\"name\":\"Order preview\""));

        assertTrue(createdEndpointResponse.body().contains("\"endpointName\":\"Order preview\""));

        assertTrue(createdEndpointResponse.body().contains("\"routePath\":\"/orders\""));

        long endpointId = readIdFromJson(createdEndpointResponse.body(), "endpointId");

        assertEquals(400, sendHttpRequest("POST", createEndpointPath, requestBody.replace("\"Order preview\"", "\" \"")).statusCode());

        assertEquals(400, sendHttpRequest("POST", createEndpointPath, requestBody.replace("{\"name\"", "{\"workspaceId\":999999,\"name\"")).statusCode());

        assertEquals(404, sendHttpRequest("POST", "/api/workspaces/999999/endpoints", requestBody).statusCode());

        assertEquals(409, sendHttpRequest("POST", createEndpointPath, requestBody).statusCode());

        HttpResponse<String> httpResponse = sendHttpRequest("POST", "/mock/workspaces/" + workspaceId + "/orders",
            "{\"productId\":42,\"quantity\":2,\"password\":\"do-not-store\",\"nested\":{\"token\":\"private\"}}");

        assertEquals(201, httpResponse.statusCode());

        long loggingDeadlineNanoseconds = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);

        while (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM request_logs WHERE endpoint_id = ?", Long.class, endpointId) == 0 && System.nanoTime() < loggingDeadlineNanoseconds) Thread.sleep(20);

        String savedBodySample = jdbcTemplate.queryForObject("SELECT request_body_sample FROM request_logs WHERE endpoint_id = ?", String.class, endpointId);

        assertEquals("{\"productId\":42,\"quantity\":2}", savedBodySample);

        assertEquals(413, sendHttpRequest("POST", "/mock/workspaces/" + workspaceId + "/orders", "x".repeat(32769)).statusCode());
    }
}
