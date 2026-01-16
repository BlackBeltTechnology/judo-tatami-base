# JVM Profiler Integration for JUnit Tests

This specification describes a reusable profiling library that integrates with JUnit 5 to automatically capture CPU profiles during test execution. The captured profiles use a collapsed stack format optimized for LLM-based analysis.

## Overview

The profiler extension hooks into the JUnit 5 test lifecycle to automatically start/stop profiling around test methods. This enables performance analysis of transformation tests (PSM2ASM, ASM2RDBMS, etc.) without requiring manual instrumentation.

## Architecture

```
judo-tatami-test-utils/
├── src/main/java/hu/blackbelt/judo/tatami/test/
│   ├── profiler/
│   │   ├── ProfilingExtension.java       # JUnit 5 extension
│   │   ├── ProfileAnalyzer.java          # Profile output processing
│   │   ├── ProfileConfig.java            # Configuration options
│   │   └── ProfileReport.java            # Report data structure
│   └── util/
│       └── (existing utilities)
```

## Implementation

### 1. JUnit 5 Extension

```java
package hu.blackbelt.judo.tatami.test.profiler;

import org.junit.jupiter.api.extension.*;
import one.profiler.AsyncProfiler;

import java.nio.file.Path;

public class ProfilingExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

    private static final String OUTPUT_PATH = "target/profiler-output/";
    private AsyncProfiler profiler;

    @Override
    public void beforeTestExecution(ExtensionContext context) throws Exception {
        profiler = AsyncProfiler.getInstance();

        // Start CPU profiling with 1ms sampling interval
        profiler.start(AsyncProfiler.EVENT_CPU, 1_000_000);
    }

    @Override
    public void afterTestExecution(ExtensionContext context) throws Exception {
        profiler.stop();

        String testClass = context.getRequiredTestClass().getSimpleName();
        String testMethod = context.getDisplayName();
        String fileName = OUTPUT_PATH + testClass + "_" + testMethod + ".txt";

        // Output in collapsed format (optimal for LLM token efficiency)
        profiler.execute("dump,file=" + fileName + ",output=collapsed");

        System.out.println("[Profiler] Output: " + fileName);
    }
}
```

### 2. Configuration Options

```java
package hu.blackbelt.judo.tatami.test.profiler;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProfileConfig {

    @Builder.Default
    String outputPath = "target/profiler-output/";

    @Builder.Default
    String outputFormat = "collapsed";  // collapsed | flamegraph | jfr

    @Builder.Default
    long samplingInterval = 1_000_000;  // nanoseconds (1ms default)

    @Builder.Default
    String event = "cpu";  // cpu | wall | alloc | lock

    @Builder.Default
    long thresholdMs = 100;  // Only profile tests exceeding this duration

    @Builder.Default
    boolean enabled = true;
}
```

### 3. Conditional Profiling Annotation

```java
package hu.blackbelt.judo.tatami.test.profiler;

import org.junit.jupiter.api.extension.ExtendWith;
import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(ProfilingExtension.class)
public @interface Profile {

    /** Minimum test duration (ms) to trigger profiling */
    long thresholdMs() default 0;

    /** Output format: collapsed, flamegraph, jfr */
    String format() default "collapsed";

    /** Profiling event type: cpu, wall, alloc, lock */
    String event() default "cpu";
}
```

## Usage in Transformation Tests

### Basic Usage

Add the `@Profile` annotation to enable automatic profiling:

```java
package hu.blackbelt.judo.tatami.psm2asm;

import hu.blackbelt.judo.tatami.test.profiler.Profile;
import org.junit.jupiter.api.Test;

@Profile
class Psm2AsmPerformanceTest {

    @Test
    void testLargeModelTransformation() {
        // This test will be automatically profiled
        Psm2AsmWork work = new Psm2AsmWork(largeModel);
        work.execute();
    }
}
```

### Conditional Profiling (Threshold-Based)

Only profile tests that exceed a time threshold:

```java
@Profile(thresholdMs = 500)
class Psm2AsmPerformanceTest {

    @Test
    void testSmallModel() {
        // Not profiled if completes under 500ms
    }

    @Test
    void testLargeModel() {
        // Profiled if exceeds 500ms
    }
}
```

### Method-Level Profiling

Override class-level settings per method:

```java
@Profile(format = "collapsed")
class TransformationBenchmarkTest {

    @Test
    @Profile(format = "flamegraph", event = "wall")
    void testWithWallClock() {
        // Uses wall-clock profiling, outputs flamegraph
    }
}
```

## Maven Dependencies

Add to `judo-tatami-test-utils/pom.xml`:

```xml
<dependencies>
    <!-- Existing dependencies -->

    <!-- JUnit 5 API for extension -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter-api</artifactId>
    </dependency>

    <!-- async-profiler Java bindings -->
    <dependency>
        <groupId>tools.profiler</groupId>
        <artifactId>async-profiler</artifactId>
        <version>3.0</version>
    </dependency>
</dependencies>
```

## System Properties

Configure profiling behavior via system properties:

```bash
# Enable/disable profiling globally
-Djudo.test.profiler.enabled=true

# Output directory
-Djudo.test.profiler.outputPath=target/profiler-output/

# Default format (collapsed | flamegraph | jfr)
-Djudo.test.profiler.format=collapsed

# Sampling interval in nanoseconds
-Djudo.test.profiler.interval=1000000

# Threshold in milliseconds (only profile slow tests)
-Djudo.test.profiler.thresholdMs=100
```

## Running Profiled Tests

```bash
# Run with profiling enabled
mvn test -pl judo-tatami-psm2asm -Dtest=*PerformanceTest -Djudo.test.profiler.enabled=true

# Run Zeta vs ETL comparison with profiling
mvn test -pl judo-tatami-psm2asm \
    -Dtest=Psm2AsmDualTransformationTest \
    -Djudo.test.profiler.enabled=true \
    -Djudo.test.profiler.thresholdMs=50
```

## Output Formats

### Collapsed Stack Format (Default)

Optimal for LLM analysis due to minimal token usage:

```
java/lang/Thread.run;hu/blackbelt/judo/tatami/psm2asm/Psm2AsmWork.execute;... 1234
java/lang/Thread.run;org/eclipse/epsilon/etl/EtlModule.execute;... 5678
```

### Flamegraph Format

Generates SVG flamegraph for visual analysis:

```bash
-Djudo.test.profiler.format=flamegraph
# Output: target/profiler-output/TestClass_testMethod.svg
```

### JFR Format

Java Flight Recorder format for JDK Mission Control:

```bash
-Djudo.test.profiler.format=jfr
# Output: target/profiler-output/TestClass_testMethod.jfr
```

## LLM Analysis

The collapsed stack format is optimized for LLM consumption. There are two primary usage patterns:

### Usage Pattern 1: External LLM Caller (Recommended)

The profiler outputs data in a format that **any external LLM** (Claude Code, Cursor, Copilot, etc.) can read and analyze directly. No integrated LLM is required.

**Workflow:**
1. Run tests with profiling enabled
2. Profile data is written to `target/profiler-output/`
3. Your IDE's LLM assistant reads the output files
4. Ask the LLM to analyze the profile data

**Example with Claude Code:**
```bash
# Run profiled tests
mvn test -Dtest=Psm2AsmPerformanceTest -Djudo.test.profiler.enabled=true

# Then ask Claude Code:
# "Analyze the profile at target/profiler-output/Psm2AsmPerformanceTest_testLargeModel.txt
#  and suggest optimizations"
```

**Why this works well:**
- Collapsed format is token-efficient (minimal overhead)
- External LLMs have full codebase context
- No API keys or configuration needed in the library
- Works with any LLM tool the developer prefers

### Usage Pattern 2: Integrated LLM Analyzer (Optional)

For automated analysis during test runs, the library can optionally call an LLM API directly. This is **completely optional** and disabled by default.

### Basic Profile Analyzer (No LLM Required)

Local hotspot detection without any external API:

```java
package hu.blackbelt.judo.tatami.test.profiler;

public class ProfileAnalyzer {

    /**
     * Analyzes a collapsed stack trace locally (no LLM required).
     * Returns top hotspots for manual review or external LLM consumption.
     */
    public static String analyze(Path collapsedProfile) {
        String content = Files.readString(collapsedProfile);
        List<Hotspot> hotspots = parseHotspots(content, 10);

        StringBuilder summary = new StringBuilder();
        summary.append("=== Profile Analysis ===\n");
        summary.append("Top CPU hotspots:\n");

        for (Hotspot h : hotspots) {
            summary.append(String.format("  %5.1f%% %s\n", h.percentage(), h.method()));
        }

        return summary.toString();
    }

    private static List<Hotspot> parseHotspots(String collapsed, int limit) {
        return collapsed.lines()
            .map(ProfileAnalyzer::parseLine)
            .collect(groupingBy(Sample::leafMethod, summingLong(Sample::count)))
            .entrySet().stream()
            .sorted(comparingLong(e -> -e.getValue()))
            .limit(limit)
            .map(e -> new Hotspot(e.getKey(), e.getValue()))
            .toList();
    }
}
```

### Integrated LLM Extension (Optional)

For automatic AI-powered analysis after each test. **Disabled by default.**

```java
package hu.blackbelt.judo.tatami.test.profiler;

import one.profiler.AsyncProfiler;
import org.junit.jupiter.api.extension.*;
import java.net.http.*;
import java.net.URI;

public class LlmProfilerExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

    private final AsyncProfiler profiler = AsyncProfiler.getInstance();

    // Disabled by default - must be explicitly enabled
    private static final boolean LLM_ENABLED =
        Boolean.parseBoolean(System.getProperty("judo.test.profiler.llm.enabled", "false"));

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        profiler.start(AsyncProfiler.EVENT_CPU, 10_000_000);
    }

    @Override
    public void afterTestExecution(ExtensionContext context) throws Exception {
        profiler.stop();
        String profileData = profiler.execute("dump,output=collapsed");

        // Always write output for external LLM consumption
        writeProfileOutput(context, profileData);

        // Optionally call integrated LLM
        if (LLM_ENABLED) {
            String truncatedData = profileData.lines().limit(100)
                .reduce("", (a, b) -> a + "\n" + b);
            String insight = callLlmAnalyzer(context.getDisplayName(), truncatedData);
            System.out.println("\n--- AI Performance Insight ---");
            System.out.println(insight);
        }
    }

    private String callLlmAnalyzer(String testName, String data) throws Exception {
        LlmProvider provider = LlmProviderFactory.create();
        String prompt = "Analyze this Java profiler collapsed stack for '" + testName + "'. " +
                        "Identify the top bottleneck and suggest a fix:\n" + data;
        return provider.complete(prompt);
    }
}
```

### Multi-Provider LLM Support

The integrated analyzer supports multiple LLM providers via a pluggable architecture:

| Provider | Endpoint Property | Model Property | API Key Env Var |
|----------|-------------------|----------------|-----------------|
| OpenAI | `https://api.openai.com/v1` | `gpt-4o-mini` | `OPENAI_API_KEY` |
| Anthropic | `https://api.anthropic.com/v1` | `claude-3-haiku-20240307` | `ANTHROPIC_API_KEY` |
| OpenRouter | `https://openrouter.ai/api/v1` | `anthropic/claude-3-haiku` | `OPENROUTER_API_KEY` |
| DeepSeek | `https://api.deepseek.com/v1` | `deepseek-chat` | `DEEPSEEK_API_KEY` |
| MiniMax | `https://api.minimax.chat/v1` | `abab6.5s-chat` | `MINIMAX_API_KEY` |
| Groq | `https://api.groq.com/openai/v1` | `llama-3.1-70b-versatile` | `GROQ_API_KEY` |
| Together | `https://api.together.xyz/v1` | `meta-llama/Llama-3-70b-chat-hf` | `TOGETHER_API_KEY` |
| Local (Ollama) | `http://localhost:11434/v1` | `llama3.1` | (none) |

**Configuration via system properties:**

```bash
# Select provider
-Djudo.test.profiler.llm.provider=openrouter

# Or configure custom endpoint (OpenAI-compatible)
-Djudo.test.profiler.llm.endpoint=https://api.openrouter.ai/v1/chat/completions
-Djudo.test.profiler.llm.model=anthropic/claude-3-haiku
-Djudo.test.profiler.llm.apiKeyEnv=OPENROUTER_API_KEY
```

### Provider Factory Implementation

```java
package hu.blackbelt.judo.tatami.test.profiler;

public interface LlmProvider {
    String complete(String prompt) throws Exception;
}

public class LlmProviderFactory {

    private static final Map<String, ProviderConfig> PROVIDERS = Map.of(
        "openai", new ProviderConfig("https://api.openai.com/v1/chat/completions",
                                     "gpt-4o-mini", "OPENAI_API_KEY"),
        "anthropic", new ProviderConfig("https://api.anthropic.com/v1/messages",
                                        "claude-3-haiku-20240307", "ANTHROPIC_API_KEY"),
        "openrouter", new ProviderConfig("https://openrouter.ai/api/v1/chat/completions",
                                         "anthropic/claude-3-haiku", "OPENROUTER_API_KEY"),
        "deepseek", new ProviderConfig("https://api.deepseek.com/v1/chat/completions",
                                       "deepseek-chat", "DEEPSEEK_API_KEY"),
        "minimax", new ProviderConfig("https://api.minimax.chat/v1/text/chatcompletion_v2",
                                      "abab6.5s-chat", "MINIMAX_API_KEY"),
        "groq", new ProviderConfig("https://api.groq.com/openai/v1/chat/completions",
                                   "llama-3.1-70b-versatile", "GROQ_API_KEY"),
        "together", new ProviderConfig("https://api.together.xyz/v1/chat/completions",
                                       "meta-llama/Llama-3-70b-chat-hf", "TOGETHER_API_KEY"),
        "ollama", new ProviderConfig("http://localhost:11434/v1/chat/completions",
                                     "llama3.1", null)
    );

    public static LlmProvider create() {
        String providerName = System.getProperty("judo.test.profiler.llm.provider", "openai");
        ProviderConfig config = PROVIDERS.getOrDefault(providerName, PROVIDERS.get("openai"));

        // Allow overrides
        String endpoint = System.getProperty("judo.test.profiler.llm.endpoint", config.endpoint());
        String model = System.getProperty("judo.test.profiler.llm.model", config.model());
        String apiKeyEnv = System.getProperty("judo.test.profiler.llm.apiKeyEnv", config.apiKeyEnv());

        return new OpenAiCompatibleProvider(endpoint, model, apiKeyEnv);
    }

    record ProviderConfig(String endpoint, String model, String apiKeyEnv) {}
}
```

### OpenAI-Compatible Provider

Most providers use OpenAI-compatible APIs:

```java
package hu.blackbelt.judo.tatami.test.profiler;

import java.net.http.*;
import java.net.URI;

public class OpenAiCompatibleProvider implements LlmProvider {

    private final String endpoint;
    private final String model;
    private final String apiKey;
    private final HttpClient client = HttpClient.newHttpClient();

    public OpenAiCompatibleProvider(String endpoint, String model, String apiKeyEnv) {
        this.endpoint = endpoint;
        this.model = model;
        this.apiKey = apiKeyEnv != null ? System.getenv(apiKeyEnv) : null;
    }

    @Override
    public String complete(String prompt) throws Exception {
        String body = """
            {
                "model": "%s",
                "messages": [{"role": "user", "content": "%s"}],
                "max_tokens": 500
            }
            """.formatted(model, escapeJson(prompt));

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));

        if (apiKey != null && !apiKey.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }

        HttpResponse<String> response = client.send(
            requestBuilder.build(),
            HttpResponse.BodyHandlers.ofString()
        );

        return parseResponse(response.body());
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String parseResponse(String json) {
        // Simple extraction - use a JSON library in production
        int start = json.indexOf("\"content\":\"") + 11;
        int end = json.indexOf("\"", start);
        return json.substring(start, end).replace("\\n", "\n");
    }
}
```

### Maven Dependencies for LLM Integration (Optional)

Only needed if using the integrated LLM analyzer:

```xml
<!-- Optional: LangChain4j for cleaner multi-provider LLM API -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>0.35.0</version>
    <optional>true</optional>
</dependency>

<!-- OpenAI provider -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>0.35.0</version>
    <optional>true</optional>
</dependency>

<!-- Anthropic provider -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-anthropic</artifactId>
    <version>0.35.0</version>
    <optional>true</optional>
</dependency>

<!-- Ollama (local) provider -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-ollama</artifactId>
    <version>0.35.0</version>
    <optional>true</optional>
</dependency>
```

### Running with Integrated LLM

```bash
# Enable integrated LLM with OpenRouter
mvn test -Dtest=*PerformanceTest \
    -Djudo.test.profiler.enabled=true \
    -Djudo.test.profiler.llm.enabled=true \
    -Djudo.test.profiler.llm.provider=openrouter

# Use DeepSeek for cost-effective analysis
mvn test -Dtest=*PerformanceTest \
    -Djudo.test.profiler.llm.enabled=true \
    -Djudo.test.profiler.llm.provider=deepseek

# Use local Ollama (no API key needed)
mvn test -Dtest=*PerformanceTest \
    -Djudo.test.profiler.llm.enabled=true \
    -Djudo.test.profiler.llm.provider=ollama
```

## Auto-Registration via Service Loader

For zero-configuration profiling across all projects, use the Java Service Loader mechanism.

### Service Provider Configuration

Create the file:
```
src/main/resources/META-INF/services/org.junit.jupiter.api.extension.Extension
```

With contents:
```
hu.blackbelt.judo.tatami.test.profiler.ProfilingExtension
```

### Result

Any project that adds `judo-tatami-test-utils` as a test dependency will **automatically** profile every test without requiring any annotations:

```xml
<dependency>
    <groupId>hu.blackbelt.judo.tatami</groupId>
    <artifactId>judo-tatami-test-utils</artifactId>
    <scope>test</scope>
</dependency>
```

### Disabling Auto-Registration

Control via system property:

```bash
# Disable automatic profiling
-Djudo.test.profiler.autoRegister=false
```

Implementation in extension:

```java
public class ProfilingExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

    private static final boolean AUTO_REGISTER_ENABLED =
        Boolean.parseBoolean(System.getProperty("judo.test.profiler.autoRegister", "true"));

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        if (!AUTO_REGISTER_ENABLED && !hasProfileAnnotation(context)) {
            return; // Skip if auto-register disabled and no @Profile annotation
        }
        // ... profiling logic
    }
}
```

## Advanced Implementation Tips

### Token Management

Profiler data can be large. Optimize for LLM token efficiency:

```java
// Instead of full collapsed output, get only top 10 hottest methods
String topMethods = profiler.execute("flat=10");

// Or aggregate and limit programmatically
String optimizedData = profileData.lines()
    .limit(50)  // Limit stack traces
    .map(line -> truncateStackDepth(line, 10))  // Limit stack depth
    .collect(Collectors.joining("\n"));
```

### Conditional Profiling

Only profile when explicitly enabled (recommended for CI/CD):

```java
public class ProfilingExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

    private static final boolean PROFILING_ENABLED =
        Boolean.parseBoolean(System.getProperty("judo.test.profiler.enabled",
            System.getenv().getOrDefault("PROFILING_ENABLED", "false")));

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        if (!PROFILING_ENABLED) {
            return;
        }
        profiler.start(AsyncProfiler.EVENT_CPU, 10_000_000);
    }

    @Override
    public void afterTestExecution(ExtensionContext context) throws Exception {
        if (!PROFILING_ENABLED) {
            return;
        }
        // ... capture and analyze
    }
}
```

### Native Library Bundling

Bundling native libraries directly into the JAR makes the profiler "plug-and-play." Without this, developers would need to manually install async-profiler on their machines.

#### Project Structure

Include native binaries for all target platforms in `src/main/resources`:

```
judo-tatami-test-utils/
├── src/main/resources/
│   └── natives/
│       ├── linux-x64/
│       │   └── libasyncProfiler.so
│       ├── linux-arm64/
│       │   └── libasyncProfiler.so
│       ├── macos-x64/
│       │   └── libasyncProfiler.dylib
│       ├── macos-arm64/
│       │   └── libasyncProfiler.dylib
│       └── windows-x64/
│           └── asyncProfiler.dll
```

#### Native Library Loader

Java cannot load native libraries directly from a JAR, so extract to a temp directory first:

```java
package hu.blackbelt.judo.tatami.test.profiler;

import java.io.*;
import java.nio.file.*;

public class NativeLoader {

    private static volatile String extractedPath;

    /**
     * Extracts the platform-specific native library and returns its path.
     * Thread-safe and extracts only once per JVM.
     */
    public static synchronized String extractAndGetPath() throws IOException {
        if (extractedPath != null) {
            return extractedPath;
        }

        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();

        // Determine platform-specific resource path
        String resourcePath;
        String suffix;

        if (os.contains("linux")) {
            suffix = ".so";
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                resourcePath = "/natives/linux-arm64/libasyncProfiler.so";
            } else {
                resourcePath = "/natives/linux-x64/libasyncProfiler.so";
            }
        } else if (os.contains("mac")) {
            suffix = ".dylib";
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                resourcePath = "/natives/macos-arm64/libasyncProfiler.dylib";
            } else {
                resourcePath = "/natives/macos-x64/libasyncProfiler.dylib";
            }
        } else if (os.contains("win")) {
            suffix = ".dll";
            resourcePath = "/natives/windows-x64/asyncProfiler.dll";
        } else {
            throw new UnsupportedOperationException("Unsupported OS: " + os);
        }

        // Create unique temp file to avoid collisions between parallel test runs
        Path tempLib = Files.createTempFile("async-profiler-", suffix);
        tempLib.toFile().deleteOnExit();

        try (InputStream in = NativeLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new FileNotFoundException(
                    "Profiler binary not found in JAR: " + resourcePath +
                    " (OS: " + os + ", Arch: " + arch + ")"
                );
            }
            Files.copy(in, tempLib, StandardCopyOption.REPLACE_EXISTING);
        }

        extractedPath = tempLib.toAbsolutePath().toString();
        return extractedPath;
    }
}
```

#### Updated Extension with Native Loading

Update the extension to load the native library once per test suite:

```java
package hu.blackbelt.judo.tatami.test.profiler;

import one.profiler.AsyncProfiler;
import org.junit.jupiter.api.extension.*;

public class ProfilingExtension implements
        BeforeAllCallback,
        BeforeTestExecutionCallback,
        AfterTestExecutionCallback {

    private AsyncProfiler profiler;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        // Extract native library once per test suite
        String nativePath = NativeLoader.extractAndGetPath();
        this.profiler = AsyncProfiler.getInstance(nativePath);
    }

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        profiler.start(AsyncProfiler.EVENT_CPU, 10_000_000);
    }

    @Override
    public void afterTestExecution(ExtensionContext context) throws Exception {
        profiler.stop();
        String profileData = profiler.execute("dump,output=collapsed");

        // Write output for external LLM consumption
        writeProfileOutput(context, profileData);

        // Optional: call integrated LLM analyzer
        if (isLlmEnabled()) {
            String insight = analyzWithLlm(context.getDisplayName(), profileData);
            System.out.println(insight);
        }
    }
}
```

#### Alternative: Use ap-loader (Recommended)

Instead of maintaining native binaries yourself, use **ap-loader** - a community project that handles this automatically:

```xml
<dependency>
    <groupId>me.bechberger</groupId>
    <artifactId>ap-loader-all</artifactId>
    <version>3.0</version>
</dependency>
```

With ap-loader, native loading becomes one line:

```java
import me.bechberger.ap.AsyncProfilerLoader;

public class ProfilingExtension implements BeforeAllCallback, ... {

    private AsyncProfiler profiler;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        // Automatically extracts and loads the correct binary for any OS
        this.profiler = AsyncProfilerLoader.load();
    }

    // ... rest of extension
}
```

**Benefits of ap-loader:**
- Pre-packaged binaries for Linux (x64, arm64), macOS (x64, arm64), Windows
- Automatic platform detection
- No need to maintain native files in your project
- Regular updates with new async-profiler releases

#### Manual Maven Assembly (If Not Using ap-loader)

If bundling natives manually, use maven-dependency-plugin to unpack:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-dependency-plugin</artifactId>
    <executions>
        <execution>
            <id>unpack-native-libs</id>
            <phase>generate-resources</phase>
            <goals>
                <goal>unpack</goal>
            </goals>
            <configuration>
                <artifactItems>
                    <artifactItem>
                        <groupId>tools.profiler</groupId>
                        <artifactId>async-profiler</artifactId>
                        <classifier>linux-x64</classifier>
                        <type>tar.gz</type>
                        <outputDirectory>${project.build.directory}/classes/natives/linux-x64</outputDirectory>
                    </artifactItem>
                    <artifactItem>
                        <groupId>tools.profiler</groupId>
                        <artifactId>async-profiler</artifactId>
                        <classifier>linux-arm64</classifier>
                        <type>tar.gz</type>
                        <outputDirectory>${project.build.directory}/classes/natives/linux-arm64</outputDirectory>
                    </artifactItem>
                    <artifactItem>
                        <groupId>tools.profiler</groupId>
                        <artifactId>async-profiler</artifactId>
                        <classifier>macos</classifier>
                        <type>tar.gz</type>
                        <outputDirectory>${project.build.directory}/classes/natives/macos-x64</outputDirectory>
                    </artifactItem>
                </artifactItems>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Universal Workflow Summary

The complete plug-and-play workflow:

```
┌─────────────────────────────────────────────────────────────────┐
│  1. Your Library (judo-tatami-test-utils)                       │
│     - Implements ProfilingExtension                             │
│     - Bundles ap-loader OR native binaries                      │
│     - Outputs collapsed format for LLM consumption              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  2. User Project (e.g., judo-tatami-psm2asm)                    │
│     - Adds your JAR as test dependency                          │
│     - No additional setup required                              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  3. Test Execution                                              │
│     a) JUnit starts                                             │
│     b) Extension detects OS (Linux/Mac/Win) and architecture    │
│     c) Extracts correct profiler binary to temp directory       │
│     d) Profiles each test, outputs collapsed stack format       │
│     e) External LLM (Claude Code, etc.) analyzes output         │
│     f) OR integrated LLM prints optimization suggestions        │
└─────────────────────────────────────────────────────────────────┘
```

## Environment Prerequisites

### Linux

async-profiler requires access to `perf_event`:

```bash
# Check current setting
cat /proc/sys/kernel/perf_event_paranoid

# Allow profiling (requires root)
echo 1 | sudo tee /proc/sys/kernel/perf_event_paranoid

# Or run Java with capabilities
sudo setcap cap_sys_admin+ep $(which java)
```

### macOS

Requires `dtrace` access (may need SIP adjustments in development):

```bash
# Run with elevated privileges if needed
sudo mvn test -Dtest=*PerformanceTest
```

### CI/CD (GitHub Actions)

```yaml
jobs:
  performance-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Configure perf_event
        run: |
          echo 1 | sudo tee /proc/sys/kernel/perf_event_paranoid

      - name: Run performance tests with profiling
        run: |
          mvn test -Pperformance \
            -Djudo.test.profiler.enabled=true \
            -Djudo.test.profiler.thresholdMs=100

      - name: Upload profiles
        uses: actions/upload-artifact@v4
        with:
          name: profiler-output
          path: '**/target/profiler-output/'
```

## Integration with Existing Test Infrastructure

### With ModelComparator

Profile comparison tests to identify performance differences:

```java
@Profile(thresholdMs = 100)
class Psm2AsmDualTransformationTest {

    @Test
    void testEtlVsZetaPerformance() {
        // ETL transformation (profiled)
        AsmModel etlResult = runEtlTransformation(psmModel);

        // Zeta transformation (profiled)
        AsmModel zetaResult = runZetaTransformation(psmModel);

        // Compare results
        ModelComparator.assertEquivalent(etlResult, zetaResult);
    }
}
```

### With RealisticModelGenerators

Profile with generated large models:

```java
@Profile(format = "collapsed")
class LargeModelPerformanceTest {

    @Test
    void testWithRealisticModel() {
        PsmModel model = RealisticPsmModelGenerator.generate(
            GeneratorConfig.builder()
                .entityCount(500)
                .attributesPerEntity(20)
                .build()
        );

        Psm2AsmWork work = new Psm2AsmWork(model);
        work.execute();
    }
}
```

## Profiling Event Types

| Event | Description | Use Case |
|-------|-------------|----------|
| `cpu` | CPU cycles | General performance analysis |
| `wall` | Wall-clock time | I/O-bound or waiting analysis |
| `alloc` | Memory allocations | Memory optimization |
| `lock` | Lock contention | Concurrency analysis |

## Best Practices

1. **Start with collapsed format** - Most efficient for initial analysis
2. **Use thresholds in CI** - Avoid profiling fast tests
3. **Profile both ETL and Zeta** - Compare transformation engine performance
4. **Archive profiles** - Store for regression analysis
5. **Use realistic models** - Small test models may not reveal production issues

## Related Documentation

- [AGENTS.md](../AGENTS.md) - Project structure and Zeta transformation details
- [ModelComparator](src/main/java/hu/blackbelt/judo/tatami/test/util/ModelComparator.java) - ETL/Zeta comparison
- [RealisticPsmModelGenerator](src/main/java/hu/blackbelt/judo/tatami/test/RealisticPsmModelGenerator.java) - Test data generation
