package hu.blackbelt.judo.tatami.test.profiler.llm;

/**
 * Configuration for an LLM provider.
 *
 * @param endpoint  The API endpoint URL
 * @param model     The model identifier
 * @param apiKeyEnv The environment variable name containing the API key (null for no auth)
 */
public record ProviderConfig(String endpoint, String model, String apiKeyEnv) {
}
