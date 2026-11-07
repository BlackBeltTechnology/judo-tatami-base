# AGENTS.md — `judo-tatami-test-utils/src/main/java/hu/blackbelt/judo/tatami/test/profiler/llm`

LLM integration clients and provider configurations for automated test profile analysis across Anthropic and OpenAI-compatible APIs.

| File | Purpose |
| --- | --- |
| `AnthropicProvider.java` | Calls Anthropic Messages API (`claude-3-haiku-20240307`) using HTTP client; authenticates via `x-api-key` header and parses completion responses. |
| `LlmProvider.java` | Interface defining `complete(String prompt)` and `getProviderName()` for automated profile report evaluation; defines `LlmException`. |
| `LlmProviderFactory.java` | Instantiates `LlmProvider` implementations based on `judo.test.profiler.llm.provider` configuration (supports OpenAI, Anthropic, OpenRouter, DeepSeek, Ollama). |
| `OpenAiCompatibleProvider.java` | Invokes OpenAI chat completion API schema for OpenAI, OpenRouter, DeepSeek, Groq, Together, and Ollama endpoints. |
| `ProviderConfig.java` | Record encapsulating endpoint URL, default model name, and API key environment variable name. |
