package hu.blackbelt.judo.tatami.test.profiler.llm;

import hu.blackbelt.judo.tatami.test.profiler.ProfileConfig;

import java.util.Map;

/**
 * Factory for creating LLM provider instances based on configuration.
 * <p>
 * Supported providers:
 * <ul>
 *   <li>openai - OpenAI API (gpt-4o-mini)</li>
 *   <li>anthropic - Anthropic API (claude-3-haiku)</li>
 *   <li>openrouter - OpenRouter (multi-model)</li>
 *   <li>deepseek - DeepSeek API</li>
 *   <li>minimax - MiniMax API</li>
 *   <li>groq - Groq API (fast inference)</li>
 *   <li>together - Together AI</li>
 *   <li>ollama - Local Ollama instance</li>
 * </ul>
 */
public class LlmProviderFactory {

    private static final String PROPERTY_PREFIX = ProfileConfig.PROPERTY_PREFIX + "llm.";

    private static final Map<String, ProviderConfig> PROVIDERS = Map.of(
            "openai", new ProviderConfig(
                    "https://api.openai.com/v1/chat/completions",
                    "gpt-4o-mini",
                    "OPENAI_API_KEY"
            ),
            "anthropic", new ProviderConfig(
                    "https://api.anthropic.com/v1/messages",
                    "claude-3-haiku-20240307",
                    "ANTHROPIC_API_KEY"
            ),
            "openrouter", new ProviderConfig(
                    "https://openrouter.ai/api/v1/chat/completions",
                    "anthropic/claude-3-haiku",
                    "OPENROUTER_API_KEY"
            ),
            "deepseek", new ProviderConfig(
                    "https://api.deepseek.com/v1/chat/completions",
                    "deepseek-chat",
                    "DEEPSEEK_API_KEY"
            ),
            "minimax", new ProviderConfig(
                    "https://api.minimax.chat/v1/text/chatcompletion_v2",
                    "abab6.5s-chat",
                    "MINIMAX_API_KEY"
            ),
            "groq", new ProviderConfig(
                    "https://api.groq.com/openai/v1/chat/completions",
                    "llama-3.1-70b-versatile",
                    "GROQ_API_KEY"
            ),
            "together", new ProviderConfig(
                    "https://api.together.xyz/v1/chat/completions",
                    "meta-llama/Llama-3-70b-chat-hf",
                    "TOGETHER_API_KEY"
            ),
            "ollama", new ProviderConfig(
                    "http://localhost:11434/v1/chat/completions",
                    "llama3.1",
                    null // No API key required
            )
    );

    /**
     * Creates an LLM provider based on system properties.
     *
     * @return Configured LlmProvider instance
     */
    public static LlmProvider create() {
        String providerName = System.getProperty(PROPERTY_PREFIX + "provider", "openai").toLowerCase();
        ProviderConfig defaultConfig = PROVIDERS.getOrDefault(providerName, PROVIDERS.get("openai"));

        // Allow system property overrides
        String endpoint = System.getProperty(PROPERTY_PREFIX + "endpoint", defaultConfig.endpoint());
        String model = System.getProperty(PROPERTY_PREFIX + "model", defaultConfig.model());
        String apiKeyEnv = System.getProperty(PROPERTY_PREFIX + "apiKeyEnv", defaultConfig.apiKeyEnv());

        // Special handling for Anthropic (different API format)
        if ("anthropic".equals(providerName) && endpoint.equals(defaultConfig.endpoint())) {
            return new AnthropicProvider(endpoint, model, apiKeyEnv);
        }

        return new OpenAiCompatibleProvider(providerName, endpoint, model, apiKeyEnv);
    }

    /**
     * Returns the list of supported provider names.
     */
    public static java.util.Set<String> getSupportedProviders() {
        return PROVIDERS.keySet();
    }
}
