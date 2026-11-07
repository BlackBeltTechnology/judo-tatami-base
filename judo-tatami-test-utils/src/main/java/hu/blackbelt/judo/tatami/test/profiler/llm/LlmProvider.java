package hu.blackbelt.judo.tatami.test.profiler.llm;

/**
 * Interface for LLM providers that can analyze profile data.
 * Implementations must handle API communication and response parsing.
 */
public interface LlmProvider {

    /**
     * Sends a prompt to the LLM and returns the completion response.
     *
     * @param prompt The prompt to send (typically includes profile data)
     * @return The LLM's response text
     * @throws LlmException If the API call fails
     */
    String complete(String prompt) throws LlmException;

    /**
     * Returns the provider name for logging purposes.
     */
    String getProviderName();

    /**
     * Exception thrown when LLM API calls fail.
     */
    class LlmException extends Exception {
        public LlmException(String message) {
            super(message);
        }

        public LlmException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
