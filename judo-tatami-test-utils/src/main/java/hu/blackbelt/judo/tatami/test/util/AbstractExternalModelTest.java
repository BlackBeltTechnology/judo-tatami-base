package hu.blackbelt.judo.tatami.test.util;

/*-
 * #%L
 * JUDO Tatami parent
 * %%
 * Copyright (C) 2018 - 2024 BlackBelt Technology
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

import hu.blackbelt.judo.tatami.test.util.comparison.CalculatorOptions;
import hu.blackbelt.judo.tatami.test.util.comparison.ComparisonResult;
import hu.blackbelt.judo.tatami.test.util.comparison.ModelChecksumCalculator;
import hu.blackbelt.judo.tatami.test.util.comparison.ModelNode;
import hu.blackbelt.judo.tatami.test.util.comparison.StructuralModelComparator;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/**
 * Abstract base class for external model testing.
 *
 * <p>Provides infrastructure for parametrized testing of ETL and ZETA transformations
 * against external model files configured via a properties file.
 *
 * <p>Subclasses should:
 * <ul>
 *   <li>Implement a test method annotated with {@code @ParameterizedTest(name = "{0}")}
 *       and {@code @MethodSource("externalModels")}</li>
 *   <li>Provide an {@code externalModels()} method that calls {@code loadModelConfigs()}</li>
 *   <li>Implement model loading and transformation execution specific to the module</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>
 * public class Psm2AsmExternalModelTest extends AbstractExternalModelTest {
 *     &#64;ParameterizedTest(name = "{0}")
 *     &#64;MethodSource("externalModels")
 *     void testExternalModel(ExternalModelConfig config) {
 *         // Load PSM model, execute transformation, compare results
 *     }
 *
 *     static Stream&lt;ExternalModelConfig&gt; externalModels() {
 *         return loadModelConfigs("external-model-tests.properties", Psm2AsmExternalModelTest.class);
 *     }
 * }
 * </pre>
 *
 * <p>Subclasses should annotate themselves with {@code @Tag("performance")} for profile-based execution.
 */
@Slf4j
public abstract class AbstractExternalModelTest {

    private static final String PROPERTIES_FILE = "external-model-tests.properties";
    private static final String SEARCH_DIRECTORIES_FILE = "model-search-directories.properties";
    private static final String MODULE_ROOT_PROPERTY = "judo.test.module.root";
    private static final String SEARCH_DIRECTORIES_PROPERTY = "judo.test.model.search.directories";
    private static final String DISCOVERY_BASEDIR_PROPERTY = "judo.test.discovery.basedir";
    private static final String JSON_RESULTS_FILENAME = "comparison-results.json";

    /**
     * Record capturing per-model test results for summary reporting.
     */
    public record TestResult(
            String modelName,
            long etlTimeMs,
            long zetaTimeMs,
            double speedup,
            int etlOutputCount,
            int zetaOutputCount,
            String outputLabel,
            String comparisonResult, // "EQUIVALENT", "FAILED", "SKIPPED"
            int differenceCount
    ) {}

    /** Thread-safe list for collecting results during test execution. */
    private final List<TestResult> testResults = new CopyOnWriteArrayList<>();

    /**
     * Records a test result for summary reporting.
     */
    protected void recordResult(String modelName, long etlTimeMs, long zetaTimeMs,
                                int etlOutputCount, int zetaOutputCount, String outputLabel,
                                String comparisonResult, int differenceCount) {
        double speedup = zetaTimeMs > 0 ? (double) etlTimeMs / zetaTimeMs : 0;
        testResults.add(new TestResult(modelName, etlTimeMs, zetaTimeMs, speedup,
                etlOutputCount, zetaOutputCount, outputLabel, comparisonResult, differenceCount));
    }

    /**
     * Clears collected results. Call at the beginning of each test factory.
     */
    protected void clearResults() {
        testResults.clear();
    }

    /**
     * Returns the collected results (unmodifiable).
     */
    protected List<TestResult> getResults() {
        return Collections.unmodifiableList(testResults);
    }

    // Structural comparison system properties
    /** System property to enable structural comparison (default: false) */
    public static final String PROP_STRUCTURAL_COMPARISON = "judo.test.comparison.structural";
    /** System property to enable JSON export of model structures (default: false) */
    public static final String PROP_STRUCTURAL_EXPORT_JSON = "judo.test.structural.exportJson";
    /** System property for JSON export output directory (default: target/comparison) */
    public static final String PROP_STRUCTURAL_OUTPUT_DIR = "judo.test.structural.outputDir";

    // Transformation mode system properties
    /** System property to select transformation mode: ZETA, ETL, or DUAL (default: DUAL) */
    public static final String PROP_TRANSFORMATION_MODE = "judo.test.transformation.mode";
    /** System property to enable JVM profiling (default: false) */
    public static final String PROP_PROFILER_ENABLED = "judo.test.profiler.enabled";

    private static final String DEFAULT_OUTPUT_DIR = "target/comparison";

    /**
     * Transformation mode for external model tests.
     * <p>
     * This enum mirrors {@code hu.blackbelt.judo.tatami.core.TransformationMode}
     * but is defined locally to avoid adding a dependency on judo-tatami-core.
     * </p>
     */
    public enum TestTransformationMode {
        /**
         * Run only ZETA (Java) transformation.
         * <p>Recommended for profiling to avoid polluting JVM metrics with ETL overhead.</p>
         */
        ZETA,

        /**
         * Run only ETL (Epsilon) transformation.
         * <p>Use for legacy testing or debugging ETL-specific issues.</p>
         */
        ETL,

        /**
         * Run both ETL and ZETA transformations and compare results.
         * <p>Default mode for validation and parity testing.</p>
         */
        DUAL;

        /**
         * Checks if this mode runs the ZETA transformation.
         * @return true if ZETA or DUAL mode
         */
        public boolean isZeta() {
            return this == ZETA || this == DUAL;
        }

        /**
         * Checks if this mode runs the ETL transformation.
         * @return true if ETL or DUAL mode
         */
        public boolean isEtl() {
            return this == ETL || this == DUAL;
        }

        /**
         * Checks if this mode compares results from both engines.
         * @return true if DUAL mode
         */
        public boolean shouldCompare() {
            return this == DUAL;
        }
    }

    /**
     * Loads model configurations from a properties file.
     *
     * <p>The properties file format supports:
     * <ul>
     *   <li>Simple format: {@code modelName=path}</li>
     *   <li>Extended format: {@code modelName=path;param1=value1;param2=value2}</li>
     * </ul>
     *
     * <p>Path resolution:
     * <ul>
     *   <li>Absolute paths (starting with /) are used as-is</li>
     *   <li>Relative paths are resolved from the module base directory</li>
     * </ul>
     *
     * @param propertiesFile the name of the properties file (in classpath)
     * @param testClass the test class (used to resolve classpath and module root)
     * @return a stream of model configurations, merged from all sources with priority
     */
    protected static Stream<ExternalModelConfig> loadModelConfigs(String propertiesFile, Class<?> testClass) {
        Path moduleBaseDir = resolveModuleBaseDir(testClass);
        log.info("Module base directory: {}", moduleBaseDir);

        // 1. Load explicit models from properties file (highest priority)
        List<ExternalModelConfig> propertiesConfigs = loadFromPropertiesFile(propertiesFile, testClass, moduleBaseDir);
        log.info("Loaded {} models from properties file '{}'", propertiesConfigs.size(), propertiesFile);

        // 2. Load search directories and scan for models
        List<Path> searchDirectories = loadSearchDirectories(testClass, moduleBaseDir);
        List<ExternalModelConfig> searchDirectoryConfigs = searchDirectories.stream()
                .flatMap(AbstractExternalModelTest::scanDirectory)
                .toList();
        log.info("Discovered {} models from {} search directories", searchDirectoryConfigs.size(), searchDirectories.size());

        // 3. Load from legacy basedir system property (lowest priority)
        List<ExternalModelConfig> basedirConfigs = loadFromBasedir(moduleBaseDir);
        log.info("Discovered {} models from basedir", basedirConfigs.size());

        // Merge all sources with priority
        return mergeModelConfigs(propertiesConfigs, searchDirectoryConfigs, basedirConfigs);
    }

    /**
     * Loads model configurations from the explicit properties file.
     */
    private static List<ExternalModelConfig> loadFromPropertiesFile(String propertiesFile, Class<?> testClass, Path moduleBaseDir) {
        URL resource = testClass.getClassLoader().getResource(propertiesFile);
        if (resource == null) {
            log.debug("Properties file '{}' not found in classpath", propertiesFile);
            return List.of();
        }

        Properties properties = new Properties();
        try (InputStream is = resource.openStream()) {
            properties.load(is);
        } catch (IOException e) {
            log.warn("Failed to load properties file '{}': {}", propertiesFile, e.getMessage());
            return List.of();
        }

        if (properties.isEmpty()) {
            log.debug("Properties file '{}' is empty", propertiesFile);
            return List.of();
        }

        List<ExternalModelConfig> configs = new ArrayList<>();
        for (String modelName : properties.stringPropertyNames()) {
            String value = properties.getProperty(modelName);
            ExternalModelConfig config = parseConfig(modelName, value, moduleBaseDir);
            configs.add(config);

            if (config.exists()) {
                log.info("Configured model '{}': {} (exists)", modelName, config.modelDirectory());
            } else {
                log.warn("Configured model '{}': {} (NOT FOUND - will be skipped)", modelName, config.modelDirectory());
            }
        }

        return configs;
    }

    /**
     * Loads model configurations from the legacy basedir system property.
     */
    private static List<ExternalModelConfig> loadFromBasedir(Path moduleBaseDir) {
        String basedir = System.getProperty(DISCOVERY_BASEDIR_PROPERTY);
        if (basedir == null || basedir.trim().isEmpty()) {
            return List.of();
        }

        Path basePath;
        if (basedir.startsWith("/")) {
            basePath = Paths.get(basedir).toAbsolutePath().normalize();
        } else {
            basePath = moduleBaseDir.resolve(basedir).toAbsolutePath().normalize();
        }

        if (!Files.isDirectory(basePath)) {
            log.debug("Basedir does not exist: {}", basePath);
            return List.of();
        }

        log.info("Discovering models from basedir: {}", basePath);
        return scanDirectory(basePath).toList();
    }

    /**
     * Convenience method using the default properties file name.
     */
    protected static Stream<ExternalModelConfig> loadModelConfigs(Class<?> testClass) {
        return loadModelConfigs(PROPERTIES_FILE, testClass);
    }

    /**
     * Resolves the module base directory for path resolution.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>System property {@code judo.test.module.root}</li>
     *   <li>JUnit resource resolution (navigates from test-classes to module root)</li>
     *   <li>Current working directory (fallback)</li>
     * </ol>
     *
     * @param testClass the test class
     * @return the resolved module base directory
     */
    protected static Path resolveModuleBaseDir(Class<?> testClass) {
        // 1. Check system property override
        String moduleRootOverride = System.getProperty(MODULE_ROOT_PROPERTY);
        if (moduleRootOverride != null && !moduleRootOverride.isEmpty()) {
            Path overridePath = Paths.get(moduleRootOverride).toAbsolutePath().normalize();
            log.debug("Using module root from system property: {}", overridePath);
            return overridePath;
        }

        // 2. Try JUnit resource resolution
        try {
            URL classesUrl = testClass.getResource("/");
            if (classesUrl != null) {
                Path classesPath = Paths.get(classesUrl.toURI());
                // Navigate from target/test-classes to module root
                // e.g., /path/to/module/target/test-classes -> /path/to/module
                Path modulePath = classesPath.getParent();  // target
                if (modulePath != null) {
                    modulePath = modulePath.getParent();  // module root
                    if (modulePath != null && Files.isDirectory(modulePath)) {
                        log.debug("Resolved module root from classpath: {}", modulePath);
                        return modulePath.toAbsolutePath().normalize();
                    }
                }
            }
        } catch (URISyntaxException e) {
            log.debug("Failed to resolve module root from classpath: {}", e.getMessage());
        }

        // 3. Fallback to current working directory
        Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        log.debug("Fallback to current working directory: {}", cwd);
        return cwd;
    }

    /**
     * Parses a configuration entry from the properties file.
     *
     * @param modelName the model name (property key)
     * @param value the property value (path with optional parameters)
     * @param moduleBaseDir the module base directory for relative path resolution
     * @return the parsed configuration
     */
    private static ExternalModelConfig parseConfig(String modelName, String value, Path moduleBaseDir) {
        // Parse extended format: path;param1=value1;param2=value2
        String[] parts = value.split(";");
        String pathString = parts[0].trim();

        Map<String, String> parameters = new HashMap<>();
        for (int i = 1; i < parts.length; i++) {
            String[] paramParts = parts[i].trim().split("=", 2);
            if (paramParts.length == 2) {
                parameters.put(paramParts[0].trim(), paramParts[1].trim());
            }
        }

        // Resolve path
        Path modelDirectory;
        if (pathString.startsWith("/")) {
            // Absolute path
            modelDirectory = Paths.get(pathString).toAbsolutePath().normalize();
        } else {
            // Relative path - resolve from module base
            modelDirectory = moduleBaseDir.resolve(pathString).toAbsolutePath().normalize();
        }

        boolean exists = Files.isDirectory(modelDirectory);

        return new ExternalModelConfig(modelName, modelDirectory, exists, parameters);
    }

    /**
     * Counts all elements in a resource set.
     *
     * @param resourceSet the resource set
     * @return the total element count
     */
    protected int countElements(ResourceSet resourceSet) {
        int count = 0;
        for (var resource : resourceSet.getResources()) {
            var iterator = resource.getAllContents();
            while (iterator.hasNext()) {
                iterator.next();
                count++;
            }
        }
        return count;
    }

    /**
     * Prints performance comparison results.
     *
     * @param testName the name of the test (e.g., "PSM2ASM")
     * @param elementCount the number of input elements
     * @param etlTimeMs ETL transformation time in milliseconds
     * @param zetaTimeMs ZETA transformation time in milliseconds
     * @param etlOutputCount ETL output element count
     * @param zetaOutputCount ZETA output element count
     * @param outputLabel label for output count (e.g., "classifiers", "tables")
     */
    protected void printResults(String testName, int elementCount,
                                long etlTimeMs, long zetaTimeMs,
                                int etlOutputCount, int zetaOutputCount,
                                String outputLabel) {
        log.info("");
        log.info("================================================================");
        log.info("RESULTS: {} ({} elements)", testName, elementCount);
        log.info("================================================================");
        log.info("");
        log.info("                    ETL              ZETA           Difference");
        log.info("----------------------------------------------------------------");
        log.info("Time:         {}ms       {}ms       {}ms ({}%)",
                String.format("%8d", etlTimeMs),
                String.format("%8d", zetaTimeMs),
                String.format("%+8d", zetaTimeMs - etlTimeMs),
                String.format("%+.1f", etlTimeMs > 0 ? ((double) (zetaTimeMs - etlTimeMs) / etlTimeMs) * 100 : 0));
        log.info("{}:  {}       {}",
                String.format("%-12s", outputLabel),
                String.format("%8d", etlOutputCount),
                String.format("%8d", zetaOutputCount));
        log.info("");

        double etlThroughput = etlTimeMs > 0 ? elementCount / (etlTimeMs / 1000.0) : 0;
        double zetaThroughput = zetaTimeMs > 0 ? elementCount / (zetaTimeMs / 1000.0) : 0;
        log.info("Throughput:   {}/s       {}/s",
                String.format("%8.0f", etlThroughput),
                String.format("%8.0f", zetaThroughput));
        log.info("");

        if (zetaTimeMs < etlTimeMs && etlTimeMs > 0) {
            double speedup = (double) etlTimeMs / zetaTimeMs;
            log.info(">>> ZETA is {}x FASTER than ETL <<<", String.format("%.2f", speedup));
        } else if (zetaTimeMs > etlTimeMs && zetaTimeMs > 0) {
            double slowdown = (double) zetaTimeMs / etlTimeMs;
            log.info(">>> ZETA is {}x SLOWER than ETL <<<", String.format("%.2f", slowdown));
        } else {
            log.info(">>> ETL and ZETA have EQUAL performance <<<");
        }
        log.info("================================================================");
    }

    /**
     * Logs the test header with model information.
     *
     * @param testName the name of the test (e.g., "PSM2ASM")
     * @param config the model configuration
     */
    protected void logTestHeader(String testName, ExternalModelConfig config) {
        log.info("");
        log.info("================================================================");
        log.info("{} External Model Test: {}", testName, config.modelName());
        log.info("================================================================");
        log.info("Model directory: {}", config.modelDirectory());
        log.info("Transformation mode: {}", getConfiguredTransformationMode());
        if (!config.parameters().isEmpty()) {
            log.info("Parameters: {}", config.parameters());
        }
        warnIfProfilingWithDualMode();
    }

    /**
     * Checks if the model directory exists and logs appropriately.
     * Returns false if the test should be skipped.
     *
     * @param config the model configuration
     * @return true if the model exists and test should proceed
     */
    protected boolean checkModelExists(ExternalModelConfig config) {
        if (!config.exists()) {
            log.warn("Model directory does not exist, skipping: {}", config.modelDirectory());
            return false;
        }
        return true;
    }

    // ========================================================================
    // Transformation Mode Support
    // ========================================================================

    /**
     * Gets the configured transformation mode from the system property.
     * <p>
     * Reads the {@code judo.test.transformation.mode} system property.
     * Defaults to {@link TestTransformationMode#DUAL} for backward compatibility.
     * </p>
     *
     * @return the configured transformation mode (case-insensitive)
     */
    public static TestTransformationMode getConfiguredTransformationMode() {
        String value = System.getProperty(PROP_TRANSFORMATION_MODE);
        if (value == null || value.trim().isEmpty()) {
            return TestTransformationMode.DUAL;
        }
        try {
            return TestTransformationMode.valueOf(value.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid transformation mode '{}', defaulting to DUAL", value);
            return TestTransformationMode.DUAL;
        }
    }

    /**
     * Checks if ETL transformation should be executed based on configured mode.
     *
     * @return true if mode is ETL or DUAL
     */
    public static boolean shouldRunEtl() {
        return getConfiguredTransformationMode().isEtl();
    }

    /**
     * Checks if ZETA transformation should be executed based on configured mode.
     *
     * @return true if mode is ZETA or DUAL
     */
    public static boolean shouldRunZeta() {
        return getConfiguredTransformationMode().isZeta();
    }

    /**
     * Checks if results from both engines should be compared.
     *
     * @return true if mode is DUAL
     */
    public static boolean shouldCompareResults() {
        return getConfiguredTransformationMode().shouldCompare();
    }

    /**
     * Checks if JVM profiling is enabled via system property.
     *
     * @return true if profiling is enabled (default: false)
     */
    public static boolean isProfilingEnabled() {
        return Boolean.parseBoolean(System.getProperty(PROP_PROFILER_ENABLED, "false"));
    }

    /**
     * Logs a warning if profiling is enabled but mode is DUAL.
     * <p>
     * Running both ETL and ZETA during profiling pollutes JVM metrics with
     * ETL/Epsilon interpreter overhead, making ZETA bottleneck analysis difficult.
     * </p>
     */
    protected void warnIfProfilingWithDualMode() {
        if (isProfilingEnabled() && getConfiguredTransformationMode() == TestTransformationMode.DUAL) {
            log.warn("Profiling in DUAL mode - consider using ZETA mode (-D{}=ZETA) for accurate metrics",
                    PROP_TRANSFORMATION_MODE);
        }
    }

    // ========================================================================
    // Structural Comparison Support
    // ========================================================================

    /**
     * Checks if structural comparison is enabled via system property.
     *
     * @return true if structural comparison is enabled (default: false)
     */
    public static boolean isStructuralComparisonEnabled() {
        return Boolean.parseBoolean(System.getProperty(PROP_STRUCTURAL_COMPARISON, "false"));
    }

    /**
     * Checks if JSON export is enabled via system property.
     *
     * @return true if JSON export is enabled (default: false)
     */
    public static boolean isJsonExportEnabled() {
        return Boolean.parseBoolean(System.getProperty(PROP_STRUCTURAL_EXPORT_JSON, "false"));
    }

    /**
     * Gets the output directory for JSON exports.
     *
     * @return the output directory path
     */
    public static Path getJsonOutputDirectory() {
        String outputDir = System.getProperty(PROP_STRUCTURAL_OUTPUT_DIR, DEFAULT_OUTPUT_DIR);
        return Paths.get(outputDir);
    }

    /**
     * Compares two EMF resources using structural comparison with checksums.
     *
     * <p>This method uses {@link ModelChecksumCalculator} to build structural representations
     * of both resources, then uses {@link StructuralModelComparator} to compare them.
     *
     * <p>On failure, LLM-friendly output is logged for analysis.
     *
     * @param expected the expected (reference) resource
     * @param actual the actual (transformed) resource
     * @return the comparison result
     */
    protected ComparisonResult compareModelsStructural(Resource expected, Resource actual) {
        log.info("Performing structural comparison...");

        // Configure options to ignore attributes that may differ between ETL and ZETA
        // but are semantically equivalent (e.g., null vs empty string for documentation)
        CalculatorOptions options = new CalculatorOptions()
                .ignore("*.*#documentation");  // Ignore documentation attribute (null vs "" difference)

        ModelChecksumCalculator calc = new ModelChecksumCalculator();
        ModelNode expectedNode = calc.calculate(expected, options);
        ModelNode actualNode = calc.calculate(actual, options);

        StructuralModelComparator comparator = new StructuralModelComparator();
        ComparisonResult result = comparator.compare(expectedNode, actualNode);

        if (result.isMatch()) {
            log.info("Structural comparison: MATCH (checksums equal)");
        } else {
            log.error("Structural comparison: {} difference(s) found", result.getDifferenceCount());
            log.error("LLM-friendly output:\n{}", comparator.formatForLLM(result));
        }

        return result;
    }

    /**
     * Exports a model's structural representation to a JSON file.
     *
     * <p>The file is written to the configured output directory with the given filename.
     * If the directory doesn't exist, it will be created.
     *
     * @param resource the resource to export
     * @param filename the output filename (without path)
     * @return the path to the written file, or null if export is disabled or fails
     */
    protected Path exportModelStructure(Resource resource, String filename) {
        if (!isJsonExportEnabled()) {
            log.debug("JSON export is disabled");
            return null;
        }

        try {
            Path outputDir = getJsonOutputDirectory();
            Files.createDirectories(outputDir);

            ModelChecksumCalculator calc = new ModelChecksumCalculator();
            ModelNode node = calc.calculate(resource);
            String json = calc.toJson(node);

            Path outputPath = outputDir.resolve(filename);
            Files.writeString(outputPath, json);
            log.info("Exported model structure to: {}", outputPath);
            return outputPath;
        } catch (IOException e) {
            log.warn("Failed to export model structure: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Exports both expected and actual model structures to JSON files.
     *
     * @param expected the expected resource
     * @param actual the actual resource
     * @param baseName the base name for output files (will append -expected.json and -actual.json)
     */
    protected void exportModelStructures(Resource expected, Resource actual, String baseName) {
        exportModelStructure(expected, baseName + "-expected.json");
        exportModelStructure(actual, baseName + "-actual.json");
    }

    // ========================================================================
    // Model Discovery
    // ========================================================================

    /**
     * Discovers model files by scanning a directory for subdirectories that follow a naming convention.
     *
     * <p>For each top-level subdirectory in {@code baseDir}, checks if
     * {@code {name}/{conventionSubPath}/{name}-{modelType}.model} exists.
     *
     * @param baseDir the base directory to scan (resolved relative to module root)
     * @param modelType the model type suffix (e.g., "esm", "psm")
     * @param conventionSubPath the subdirectory path convention (e.g., "application/model/target/generated-resources/model")
     * @return a stream of discovered model configurations
     */
    protected static Stream<ExternalModelConfig> discoverModels(Path baseDir, String modelType, String conventionSubPath) {
        if (!Files.isDirectory(baseDir)) {
            log.warn("Model discovery base directory does not exist: {}", baseDir);
            return Stream.empty();
        }

        log.info("Discovering models in: {}", baseDir);

        List<ExternalModelConfig> configs = new ArrayList<>();
        try (var entries = Files.list(baseDir)) {
            entries.filter(Files::isDirectory)
                    .sorted()
                    .forEach(dir -> {
                        String name = dir.getFileName().toString();
                        Path modelDir = dir.resolve(conventionSubPath);
                        // Try "{name}-{modelType}.model" first (e.g., rackinspect-esm.model)
                        Path modelFile = modelDir.resolve(name + "-" + modelType + ".model");
                        if (!Files.isRegularFile(modelFile)) {
                            // Fallback: try "{name}.model" (e.g., ActionGroupTest.model)
                            modelFile = modelDir.resolve(name + ".model");
                        }

                        if (Files.isRegularFile(modelFile)) {
                            configs.add(new ExternalModelConfig(name, modelDir.toAbsolutePath().normalize(), true, Map.of()));
                            log.info("Discovered model '{}': {}", name, modelDir);
                        }
                    });
        } catch (IOException e) {
            log.warn("Failed to scan directory '{}': {}", baseDir, e.getMessage());
            return Stream.empty();
        }

        log.info("Discovered {} models", configs.size());
        return configs.stream();
    }

    /**
     * Loads search directories from both the properties file and Maven system property.
     *
     * <p>The Maven system property ({@code -Djudo.test.model.search.directories}) takes priority
     * over the properties file. If the system property is set, the properties file is ignored.
     *
     * @param testClass the test class (used to resolve classpath)
     * @param moduleBaseDir the module base directory for relative path resolution
     * @return a list of existing directory paths to scan
     */
    private static List<Path> loadSearchDirectories(Class<?> testClass, Path moduleBaseDir) {
        // First check Maven system property (takes priority)
        String systemPropertyDirs = System.getProperty(SEARCH_DIRECTORIES_PROPERTY);
        if (systemPropertyDirs != null && !systemPropertyDirs.trim().isEmpty()) {
            log.debug("Using search directories from system property: {}", systemPropertyDirs);
            return parseDirectoryList(systemPropertyDirs, moduleBaseDir);
        }

        // Fall back to properties file
        URL resource = testClass.getClassLoader().getResource(SEARCH_DIRECTORIES_FILE);
        if (resource == null) {
            log.debug("Search directories file '{}' not found in classpath", SEARCH_DIRECTORIES_FILE);
            return List.of();
        }

        try (InputStream is = resource.openStream()) {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            log.debug("Loaded search directories from properties file");
            return parseDirectoryList(content, moduleBaseDir);
        } catch (IOException e) {
            log.warn("Failed to load search directories file '{}': {}", SEARCH_DIRECTORIES_FILE, e.getMessage());
            return List.of();
        }
    }

    /**
     * Parses a comma or newline-separated list of directory paths.
     *
     * <p>Paths are resolved from the module base directory. Only existing directories
     * are returned; missing directories are silently skipped.
     *
     * @param content the comma or newline-separated path string
     * @param moduleBaseDir the module base directory for relative path resolution
     * @return a list of existing directory paths
     */
    private static List<Path> parseDirectoryList(String content, Path moduleBaseDir) {
        List<Path> result = new ArrayList<>();

        // Support both comma and newline separators
        String[] paths = content.split("[,\\n\\r]+");
        for (String pathStr : paths) {
            String trimmed = pathStr.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;  // Skip empty lines and comments
            }

            Path path;
            if (trimmed.startsWith("/")) {
                // Absolute path
                path = Paths.get(trimmed).toAbsolutePath().normalize();
            } else {
                // Relative path - resolve from module base
                path = moduleBaseDir.resolve(trimmed).toAbsolutePath().normalize();
            }

            if (Files.isDirectory(path)) {
                result.add(path);
                log.debug("Added search directory: {}", path);
            } else {
                log.debug("Skipping non-existent search directory: {}", path);
            }
        }

        return result;
    }

    /**
     * Scans a directory for subdirectories containing model files.
     *
     * <p>When {@code *.model} files are found at a given level, scanning does not descend
     * deeper into that subtree (short-circuit). If no model files are found, child directories
     * are scanned recursively.
     *
     * @param searchDir the directory to scan
     * @return a stream of discovered model configurations
     */
    private static Stream<ExternalModelConfig> scanDirectory(Path searchDir) {
        List<ExternalModelConfig> configs = new ArrayList<>();
        scanDirectoryRecursive(searchDir, configs);
        return configs.stream();
    }

    /** Directory names to skip during recursive scanning (build output, VCS, etc.) */
    private static final Set<String> SKIP_DIRECTORIES = Set.of("target", "build", ".git", "node_modules", ".gradle");

    /**
     * Recursive helper for directory scanning with short-circuit behavior.
     * If the directory contains *.model files, it is added as a model and no deeper scanning occurs.
     * Otherwise, child directories are scanned recursively (skipping build output directories).
     */
    private static void scanDirectoryRecursive(Path dir, List<ExternalModelConfig> configs) {
        if (!Files.isDirectory(dir)) {
            return;
        }

        if (isModelDirectory(dir)) {
            String modelName = dir.getFileName().toString();
            configs.add(new ExternalModelConfig(modelName, dir.toAbsolutePath().normalize(), true, Map.of()));
            log.debug("Discovered model directory: {} -> {}", modelName, dir);
            return; // Short-circuit: do not scan deeper
        }

        // No model files at this level — recurse into children, skipping build output
        try (Stream<Path> children = Files.list(dir)) {
            children.filter(Files::isDirectory)
                    .filter(child -> !SKIP_DIRECTORIES.contains(child.getFileName().toString()))
                    .sorted()
                    .forEach(child -> scanDirectoryRecursive(child, configs));
        } catch (IOException e) {
            log.warn("Failed to scan directory '{}': {}", dir, e.getMessage());
        }
    }

    /**
     * Checks if a directory contains at least one model file.
     *
     * @param dir the directory to check
     * @return true if the directory contains at least one {@code *.model} file
     */
    private static boolean isModelDirectory(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            return files.anyMatch(file ->
                    Files.isRegularFile(file) && file.getFileName().toString().endsWith(".model"));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Merges model configurations from multiple sources with priority.
     *
     * <p>Higher priority sources override lower priority sources when model names conflict.
     * Priority order (highest first):
     * <ol>
     *   <li>Explicit models from properties file</li>
     *   <li>Maven system property injected directories</li>
     *   <li>Search directories from properties file</li>
     *   <li>Legacy basedir discovery</li>
     * </ol>
     *
     * @param propertiesConfigs models from explicit properties file
     * @param searchDirectoryConfigs models discovered from search directories
     * @param basedirConfigs models discovered from basedir
     * @return merged stream of configurations with priority applied
     */
    private static Stream<ExternalModelConfig> mergeModelConfigs(
            List<ExternalModelConfig> propertiesConfigs,
            List<ExternalModelConfig> searchDirectoryConfigs,
            List<ExternalModelConfig> basedirConfigs) {

        Map<String, ExternalModelConfig> merged = new LinkedHashMap<>();

        // Add in reverse priority order (lowest first, so higher priority overwrites)
        // 4. Basedir (lowest priority)
        for (ExternalModelConfig config : basedirConfigs) {
            merged.putIfAbsent(config.modelName(), config);
        }

        // 3. Search directories
        for (ExternalModelConfig config : searchDirectoryConfigs) {
            merged.put(config.modelName(), config);
        }

        // 1. Properties file (highest priority)
        for (ExternalModelConfig config : propertiesConfigs) {
            merged.put(config.modelName(), config);
        }

        // Deduplicate by directory: remove lower-priority entries that point to the same
        // modelDirectory as a properties-file entry but under a different name.
        // This happens when a search directory points directly at a leaf dir whose name
        // differs from the model file prefix (e.g. dir="model", files="rackinspect-*.model").
        Set<Path> propertiesDirectories = propertiesConfigs.stream()
                .map(ExternalModelConfig::modelDirectory)
                .collect(java.util.stream.Collectors.toSet());
        merged.values().removeIf(config ->
                propertiesDirectories.contains(config.modelDirectory())
                && propertiesConfigs.stream().noneMatch(c -> c.modelName().equals(config.modelName())));

        return merged.values().stream();
    }

    /**
     * Prints a summary table of all collected test results.
     *
     * @param moduleName the module name (e.g., "PSM2ASM")
     */
    protected void printSummary(String moduleName) {
        if (testResults.isEmpty()) {
            log.info("No results to summarize for {}", moduleName);
            return;
        }

        int passed = (int) testResults.stream().filter(r -> "EQUIVALENT".equals(r.comparisonResult())).count();
        int failed = (int) testResults.stream().filter(r -> "FAILED".equals(r.comparisonResult())).count();
        int skipped = (int) testResults.stream().filter(r -> "SKIPPED".equals(r.comparisonResult())).count();

        log.info("");
        log.info("================================================================");
        log.info("SUMMARY: {} ({} models, {} passed, {} failed, {} skipped)",
                moduleName, testResults.size(), passed, failed, skipped);
        log.info("================================================================");
        log.info(String.format("%-28s │ %8s │ %8s │ %8s │ %s",
                "Model", "ETL(ms)", "Zeta(ms)", "Speedup", "Comparison"));
        log.info("─────────────────────────────┼──────────┼──────────┼──────────┼────────────");

        long totalEtl = 0;
        long totalZeta = 0;
        for (TestResult r : testResults) {
            totalEtl += r.etlTimeMs();
            totalZeta += r.zetaTimeMs();
            String speedupStr = r.speedup() >= 1.0
                    ? String.format("%.2fx", r.speedup())
                    : String.format("%.2fx SLOW", r.speedup());
            log.info(String.format("%-28s │ %8d │ %8d │ %8s │ %s",
                    truncate(r.modelName(), 28), r.etlTimeMs(), r.zetaTimeMs(),
                    speedupStr, r.comparisonResult()));
        }

        double avgSpeedup = totalZeta > 0 ? (double) totalEtl / totalZeta : 0;
        log.info("─────────────────────────────┼──────────┼──────────┼──────────┼────────────");
        log.info(String.format("%-28s │ %8d │ %8d │ %8s │ %d/%d PASS",
                "TOTAL", totalEtl, totalZeta,
                String.format("%.2fx", avgSpeedup),
                passed, testResults.size()));
        log.info("================================================================");
    }

    /**
     * Writes test results as JSON to the given target directory.
     *
     * @param moduleName the module name
     * @param targetDir the target directory (e.g., "target/")
     */
    protected void writeJsonResults(String moduleName, Path targetDir) {
        if (testResults.isEmpty()) {
            return;
        }

        try {
            Files.createDirectories(targetDir);
            Path jsonFile = targetDir.resolve(JSON_RESULTS_FILENAME);

            String comparisonMode = System.getProperty("judo.test.comparison.mode", "STRICT");
            boolean comparisonEnabled = !"false".equalsIgnoreCase(
                    System.getProperty("judo.test.comparison.enabled", "true"));

            int passed = (int) testResults.stream().filter(r -> "EQUIVALENT".equals(r.comparisonResult())).count();
            int failed = (int) testResults.stream().filter(r -> "FAILED".equals(r.comparisonResult())).count();
            int skipped = (int) testResults.stream().filter(r -> "SKIPPED".equals(r.comparisonResult())).count();
            long totalEtl = testResults.stream().mapToLong(TestResult::etlTimeMs).sum();
            long totalZeta = testResults.stream().mapToLong(TestResult::zetaTimeMs).sum();
            double avgSpeedup = totalZeta > 0 ? (double) totalEtl / totalZeta : 0;

            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"module\": \"").append(escapeJson(moduleName)).append("\",\n");
            sb.append("  \"timestamp\": \"").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\",\n");
            sb.append("  \"comparisonMode\": \"").append(comparisonEnabled ? comparisonMode : "DISABLED").append("\",\n");
            sb.append("  \"results\": [\n");

            for (int i = 0; i < testResults.size(); i++) {
                TestResult r = testResults.get(i);
                sb.append("    {\n");
                sb.append("      \"model\": \"").append(escapeJson(r.modelName())).append("\",\n");
                sb.append("      \"etlTimeMs\": ").append(r.etlTimeMs()).append(",\n");
                sb.append("      \"zetaTimeMs\": ").append(r.zetaTimeMs()).append(",\n");
                sb.append("      \"speedup\": ").append(String.format("%.2f", r.speedup())).append(",\n");
                sb.append("      \"etlOutputCount\": ").append(r.etlOutputCount()).append(",\n");
                sb.append("      \"zetaOutputCount\": ").append(r.zetaOutputCount()).append(",\n");
                sb.append("      \"outputLabel\": \"").append(escapeJson(r.outputLabel())).append("\",\n");
                sb.append("      \"comparisonResult\": \"").append(r.comparisonResult()).append("\",\n");
                sb.append("      \"differenceCount\": ").append(r.differenceCount()).append("\n");
                sb.append("    }").append(i < testResults.size() - 1 ? "," : "").append("\n");
            }

            sb.append("  ],\n");
            sb.append("  \"summary\": {\n");
            sb.append("    \"totalModels\": ").append(testResults.size()).append(",\n");
            sb.append("    \"passed\": ").append(passed).append(",\n");
            sb.append("    \"failed\": ").append(failed).append(",\n");
            sb.append("    \"skipped\": ").append(skipped).append(",\n");
            sb.append("    \"totalEtlTimeMs\": ").append(totalEtl).append(",\n");
            sb.append("    \"totalZetaTimeMs\": ").append(totalZeta).append(",\n");
            sb.append("    \"avgSpeedup\": ").append(String.format("%.2f", avgSpeedup)).append("\n");
            sb.append("  }\n");
            sb.append("}\n");

            Files.writeString(jsonFile, sb.toString(), StandardCharsets.UTF_8);
            log.info("Results written to: {}", jsonFile);
        } catch (IOException e) {
            log.warn("Failed to write JSON results: {}", e.getMessage());
        }
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 2) + "..";
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
