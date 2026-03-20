# Profiler LLM Integration Specification

## Purpose

Provide optional LLM integration for automated profile analysis, supporting multiple providers while prioritizing external LLM tools as the primary consumption pattern.

## ADDED Requirements

### Requirement: External LLM Compatibility

The profiler output SHALL be optimized for consumption by external LLM tools.

#### Scenario: Token-efficient output format
Given profiling with collapsed format
When output is generated
Then format uses minimal tokens (no JSON wrapper, no metadata overhead)
And stack frames are semicolon-delimited
And sample counts are space-separated

#### Scenario: External LLM reads profile
Given profile output at `target/profiler-output/TestClass_testMethod.txt`
When an external LLM tool (Claude Code, Cursor, Copilot) reads the file
Then the LLM can parse and analyze the collapsed format
And the LLM can identify hotspots and suggest optimizations

---

### Requirement: Integrated LLM Disabled by Default

The integrated LLM analyzer SHALL be disabled by default to avoid API costs and complexity.

#### Scenario: Default LLM state
Given no LLM configuration is specified
When a test with profiling completes
Then no LLM API calls are made
And profile output is still written for external consumption

#### Scenario: Enable integrated LLM
Given system property `judo.test.profiler.llm.enabled=true`
And environment variable `OPENAI_API_KEY` is set
When a test with profiling completes
Then LLM API is called with truncated profile data
And analysis is printed to console

---

### Requirement: Multi-Provider LLM Support

The integrated analyzer SHALL support multiple LLM providers via a pluggable architecture.

#### Scenario: OpenAI provider
Given `judo.test.profiler.llm.provider=openai`
And `OPENAI_API_KEY` environment variable is set
When LLM analysis is triggered
Then API call goes to `api.openai.com`
And model `gpt-4o-mini` is used by default

#### Scenario: Anthropic provider
Given `judo.test.profiler.llm.provider=anthropic`
And `ANTHROPIC_API_KEY` environment variable is set
When LLM analysis is triggered
Then API call goes to `api.anthropic.com`
And model `claude-3-haiku-20240307` is used by default

#### Scenario: OpenRouter provider
Given `judo.test.profiler.llm.provider=openrouter`
And `OPENROUTER_API_KEY` environment variable is set
When LLM analysis is triggered
Then API call goes to `openrouter.ai`
And model `anthropic/claude-3-haiku` is used by default

#### Scenario: DeepSeek provider
Given `judo.test.profiler.llm.provider=deepseek`
And `DEEPSEEK_API_KEY` environment variable is set
When LLM analysis is triggered
Then API call goes to `api.deepseek.com`
And model `deepseek-chat` is used by default

#### Scenario: MiniMax provider
Given `judo.test.profiler.llm.provider=minimax`
And `MINIMAX_API_KEY` environment variable is set
When LLM analysis is triggered
Then API call goes to `api.minimax.chat`
And model `abab6.5s-chat` is used by default

#### Scenario: Groq provider
Given `judo.test.profiler.llm.provider=groq`
And `GROQ_API_KEY` environment variable is set
When LLM analysis is triggered
Then API call goes to `api.groq.com`
And model `llama-3.1-70b-versatile` is used by default

#### Scenario: Together provider
Given `judo.test.profiler.llm.provider=together`
And `TOGETHER_API_KEY` environment variable is set
When LLM analysis is triggered
Then API call goes to `api.together.xyz`
And model `meta-llama/Llama-3-70b-chat-hf` is used by default

#### Scenario: Local Ollama provider
Given `judo.test.profiler.llm.provider=ollama`
And Ollama is running locally on port 11434
When LLM analysis is triggered
Then API call goes to `localhost:11434`
And no API key is required
And model `llama3.1` is used by default

---

### Requirement: Custom Provider Configuration

The integrated analyzer SHALL support custom endpoint and model configuration.

#### Scenario: Custom endpoint override
Given `judo.test.profiler.llm.endpoint=https://custom.api.com/v1/chat`
When LLM analysis is triggered
Then API call goes to the custom endpoint

#### Scenario: Custom model override
Given `judo.test.profiler.llm.model=custom-model-name`
When LLM analysis is triggered
Then the custom model is used

#### Scenario: Custom API key environment variable
Given `judo.test.profiler.llm.apiKeyEnv=CUSTOM_API_KEY`
And `CUSTOM_API_KEY` environment variable is set
When LLM analysis is triggered
Then the custom API key is used

---

### Requirement: Token Management

The integrated analyzer SHALL manage token usage to control costs and improve accuracy.

#### Scenario: Profile data truncation
Given integrated LLM is enabled
When profile data exceeds 100 lines
Then only the first 100 lines are sent to the LLM

#### Scenario: Flat output for top methods
Given integrated LLM is enabled
And `judo.test.profiler.llm.flatLimit=10` is set
When LLM analysis is triggered
Then only top 10 hottest methods are sent (not full stacks)

---

### Requirement: LLM Analysis Output

The integrated analyzer SHALL provide clear, actionable analysis output.

#### Scenario: Console output format
Given integrated LLM is enabled
When analysis completes
Then output is prefixed with `--- AI Performance Insight ---`
And includes test name
And includes optimization suggestions

#### Scenario: Analysis prompt structure
Given integrated LLM is enabled
When analysis is triggered
Then prompt includes: test name, collapsed profile data
And asks for: top bottleneck identification, code fix suggestions
