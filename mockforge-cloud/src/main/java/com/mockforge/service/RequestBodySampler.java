package com.mockforge.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class RequestBodySampler {

    public static final int MAX_BODY_BYTES = 32768;

    public static final int MAX_SAMPLE_BYTES = 256;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private final List<String> allowedFieldNames = List.of("productId", "quantity");

    // Read only a small body. We never keep the complete request in the log.
    public String sampleRequestBody(HttpServletRequest httpRequest) throws IOException {

        if (httpRequest.getContentLengthLong() > MAX_BODY_BYTES) {
            throw createBodyTooLargeException();
        }

        // One extra byte lets us detect overflow even when Content-Length is missing.
        byte[] requestBodyBytes = httpRequest.getInputStream().readNBytes(MAX_BODY_BYTES + 1);

        if (requestBodyBytes.length > MAX_BODY_BYTES) {
            throw createBodyTooLargeException();
        }

        return sampleJsonBody(httpRequest.getContentType(), requestBodyBytes);
    }


    // Only the two approved top-level integer fields can become a saved sample.
    public String sampleJsonBody(String contentType, byte[] requestBodyBytes) {

        if (requestBodyBytes.length == 0
                || requestBodyBytes.length > MAX_BODY_BYTES
                || contentType == null) {

            return null;
        }

        try {
            MediaType requestMediaType = MediaType.parseMediaType(contentType);

            if (!"application".equalsIgnoreCase(requestMediaType.getType())
                    || !("json".equalsIgnoreCase(requestMediaType.getSubtype())
                    || requestMediaType.getSubtype().endsWith("+json"))) {

                return null;
            }

            JsonNode jsonBody = jsonMapper.readTree(requestBodyBytes);

            if (jsonBody == null || !jsonBody.isObject()) {
                return null;
            }

            Map<String, Long> approvedFields = new LinkedHashMap<>();

            for (String fieldName : allowedFieldNames) {
                JsonNode fieldValue = jsonBody.get(fieldName);

                // A string or nested object is omitted, even under an approved field name.
                if (fieldValue != null
                        && fieldValue.isIntegralNumber()
                        && fieldValue.canConvertToLong()) {

                    approvedFields.put(fieldName, fieldValue.longValue());
                }
            }

            if (approvedFields.isEmpty()) {
                return null;
            }

            String sampleJson = jsonMapper.writeValueAsString(approvedFields);

            if (sampleJson.getBytes(StandardCharsets.UTF_8).length > MAX_SAMPLE_BYTES) {
                return null;
            }

            return sampleJson;
        } catch (JacksonException | IllegalArgumentException exception) {
            // Unreadable JSON still reaches the mock; it just has no saved body sample.
            return null;
        }
    }


    private ResponseStatusException createBodyTooLargeException() {

        return new ResponseStatusException(
            HttpStatus.PAYLOAD_TOO_LARGE, "Mock request body exceeds 32 KiB");
    }
}
