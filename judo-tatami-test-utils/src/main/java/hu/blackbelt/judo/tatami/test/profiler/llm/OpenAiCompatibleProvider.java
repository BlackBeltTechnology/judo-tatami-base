package hu.blackbelt.judo.tatami.test.profiler.llm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LLM provider implementation for OpenAI-compatible APIs.
 * Works with: OpenAI, OpenRouter, DeepSeek, Groq, Together, Ollama, and others.
 */
public class OpenAiCompatibleProvider implements LlmProvider {

    private static final Logger LOG = Logger.getLogger(OpenAiCompatibleProvider.class.getName());
    private static final int TIMEOUT_SECONDS = 60;
    private static final int MAX_TOKENS = 500;

    private final String providerName;
    private final String endpoint;
    private final String model;
    private final String apiKey;
    private final HttpClient client;

    public OpenAiCompatibleProvider(String providerName, String endpoint, String model, String apiKeyEnv) {
        this.providerName = providerName;
        this.endpoint = endpoint;
        this.model = model;
        this.apiKey = apiKeyEnv != null ? System.getenv(apiKeyEnv) : null;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build();
    }

    @Override
    public String complete(String prompt) throws LlmException {
        try {
            String requestBody = buildRequestBody(prompt);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody));

            if (apiKey != null && !apiKey.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }

            HttpResponse<String> response = client.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                throw new LlmException("API returned status " + response.statusCode() + ": " + response.body());
            }

            return parseResponse(response.body());

        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "LLM API call failed", e);
            throw new LlmException("Failed to call LLM API: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return providerName;
    }

    private String buildRequestBody(String prompt) {
        // Simple JSON construction without external dependencies
        String escapedPrompt = escapeJson(prompt);
        return """
                {
                    "model": "%s",
                    "messages": [
                        {
                            "role": "user",
                            "content": "%s"
                        }
                    ],
                    "max_tokens": %d
                }
                """.formatted(model, escapedPrompt, MAX_TOKENS);
    }

    private String parseResponse(String json) throws LlmException {
        // Simple extraction of content from OpenAI-format response
        // Response format: {"choices":[{"message":{"content":"..."}}]}
        try {
            int contentStart = json.indexOf("\"content\":");
            if (contentStart < 0) {
                throw new LlmException("No content field in response: " + json);
            }

            // Find the opening quote after "content":
            int valueStart = json.indexOf("\"", contentStart + 10) + 1;
            if (valueStart <= 0) {
                throw new LlmException("Malformed content field in response");
            }

            // Find the closing quote (handling escaped quotes)
            int valueEnd = findClosingQuote(json, valueStart);
            if (valueEnd < 0) {
                throw new LlmException("Unterminated content string in response");
            }

            String content = json.substring(valueStart, valueEnd);
            return unescapeJson(content);

        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("Failed to parse response: " + e.getMessage(), e);
        }
    }

    private int findClosingQuote(String s, int start) {
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                i++; // Skip escaped character
            } else if (c == '"') {
                return i;
            }
        }
        return -1;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String unescapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }
}
