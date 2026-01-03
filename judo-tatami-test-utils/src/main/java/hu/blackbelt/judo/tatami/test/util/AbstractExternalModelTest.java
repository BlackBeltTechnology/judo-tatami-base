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

import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.resource.ResourceSet;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
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
    private static final String MODULE_ROOT_PROPERTY = "judo.test.module.root";

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
     * @return a stream of model configurations, filtered to only existing directories
     */
    protected static Stream<ExternalModelConfig> loadModelConfigs(String propertiesFile, Class<?> testClass) {
        URL resource = testClass.getClassLoader().getResource(propertiesFile);
        if (resource == null) {
            log.info("Properties file '{}' not found in classpath - no external models configured", propertiesFile);
            return Stream.empty();
        }

        Properties properties = new Properties();
        try (InputStream is = resource.openStream()) {
            properties.load(is);
        } catch (IOException e) {
            log.warn("Failed to load properties file '{}': {}", propertiesFile, e.getMessage());
            return Stream.empty();
        }

        if (properties.isEmpty()) {
            log.info("Properties file '{}' is empty - no external models configured", propertiesFile);
            return Stream.empty();
        }

        Path moduleBaseDir = resolveModuleBaseDir(testClass);
        log.info("Module base directory: {}", moduleBaseDir);

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

        // Return all configs - individual tests will skip if model doesn't exist
        // This allows JUnit to show skipped tests in the report
        return configs.stream();
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
        if (!config.parameters().isEmpty()) {
            log.info("Parameters: {}", config.parameters());
        }
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
}
