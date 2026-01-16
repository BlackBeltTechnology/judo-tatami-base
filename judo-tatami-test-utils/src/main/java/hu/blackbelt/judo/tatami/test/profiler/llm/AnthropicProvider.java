package hu.blackbelt.judo.tatami.test.profiler.llm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LLM provider implementation for Anthropic's Claude API.
 * Uses the Messages API format which differs from OpenAI's format.
 */
public class AnthropicProvider implements LlmProvider {

    private static final Logger LOG = Logger.getLogger(AnthropicProvider.class.getName());
    private static final int TIMEOUT_SECONDS = 60;
    private static final int MAX_TOKENS = 500;
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final String endpoint;
    private final String model;
    private final String apiKey;
    private final HttpClient client;

    public AnthropicProvider(String endpoint, String model, String apiKeyEnv) {
        this.endpoint = endpoint;
        this.model = model;
        this.apiKey = apiKeyEnv != null ? System.getenv(apiKeyEnv) : null;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build();
    }

    @Override
    public String complete(String prompt) throws LlmException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new LlmException("ANTHROPIC_API_KEY environment variable not set");
        }

        try {
            String requestBody = buildRequestBody(prompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new LlmException("Anthropic API returned status " + response.statusCode() + ": " + response.body());
            }

            return parseResponse(response.body());

        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Anthropic API call failed", e);
            throw new LlmException("Failed to call Anthropic API: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return "anthropic";
    }

    private String buildRequestBody(String prompt) {
        String escapedPrompt = escapeJson(prompt);
        return """
                {
                    "model": "%s",
                    "max_tokens": %d,
                    "messages": [
                        {
                            "role": "user",
                            "content": "%s"
                        }
                    ]
                }
                """.formatted(model, MAX_TOKENS, escapedPrompt);
    }

    private String parseResponse(String json) throws LlmException {
        // Anthropic response format: {"content":[{"type":"text","text":"..."}]}
        try {
            int textStart = json.indexOf("\"text\":");
            if (textStart < 0) {
                throw new LlmException("No text field in Anthropic response: " + json);
            }

            int valueStart = json.indexOf("\"", textStart + 7) + 1;
            if (valueStart <= 0) {
                throw new LlmException("Malformed text field in response");
            }

            int valueEnd = findClosingQuote(json, valueStart);
            if (valueEnd < 0) {
                throw new LlmException("Unterminated text string in response");
            }

            String content = json.substring(valueStart, valueEnd);
            return unescapeJson(content);

        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException("Failed to parse Anthropic response: " + e.getMessage(), e);
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
