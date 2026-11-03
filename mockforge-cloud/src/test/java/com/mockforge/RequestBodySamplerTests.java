package com.mockforge;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import com.mockforge.service.RequestBodySampler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestBodySamplerTests {

    private final RequestBodySampler requestBodySampler = new RequestBodySampler();

    private String sampleJsonBodyForTest(String contentType, String jsonBody) {

        return requestBodySampler.sampleJsonBody(contentType, jsonBody.getBytes(StandardCharsets.UTF_8));
    }


    @Test
    void onlyApprovedTopLevelIntegersAreStored() {

        String jsonBody = "{\"productId\":42,\"quantity\":2,\"password\":\"secret\",\"token\":\"private\","
            + "\"email\":\"private@example.invalid\",\"nested\":{\"quantity\":999}}";

        assertEquals("{\"productId\":42,\"quantity\":2}", sampleJsonBodyForTest("application/json", jsonBody));

        assertEquals("{\"productId\":42}", sampleJsonBodyForTest("application/vnd.demo+json", "{\"productId\":42}"));
    }


    @Test
    void stringsNestedDataAndUnapprovedBodiesAreOmitted() {

        assertNull(sampleJsonBodyForTest("application/json", "{\"productId\":\"secret\",\"quantity\":{\"password\":\"secret\"}}"));

        assertNull(sampleJsonBodyForTest("application/json", "{\"productId\":1.5,\"quantity\":999999999999999999999999}"));

        assertNull(sampleJsonBodyForTest("application/json", "[{\"productId\":42}]"));

        assertNull(sampleJsonBodyForTest("application/json", "not JSON"));

        assertNull(sampleJsonBodyForTest("text/plain", "{\"productId\":42}"));

        assertNull(sampleJsonBodyForTest(null, "{\"productId\":42}"));
    }


    @Test
    void oversizedBodiesAreRejectedEvenWithoutContentLength() {

        MockHttpServletRequest httpRequest = new MockHttpServletRequest() {

            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };

        httpRequest.setContentType("application/json");

        httpRequest.setContent(new byte[RequestBodySampler.MAX_BODY_BYTES + 1]);

        ResponseStatusException bodyTooLargeException = assertThrows(ResponseStatusException.class, () -> requestBodySampler.sampleRequestBody(httpRequest));

        assertEquals(413, bodyTooLargeException.getStatusCode().value());
    }
}
