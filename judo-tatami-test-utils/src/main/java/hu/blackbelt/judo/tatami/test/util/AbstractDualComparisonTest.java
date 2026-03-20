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
import org.eclipse.emf.ecore.resource.Resource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Generic base class for dual (ETL vs ZETA) transformation comparison tests.
 *
 * <p>Extends {@link AbstractExternalModelTest} to reuse its model discovery,
 * result recording, summary reporting, and JSON export infrastructure.
 *
 * <p>Subclasses implement 5 abstract methods to define how to parse source models,
 * execute ETL and ZETA transformations, extract EMF resources, and name the module.
 * The base class provides:
 * <ul>
 *   <li>{@code @TestFactory compareExternalModels()} — full orchestration with warmup,
 *       timed ETL, timed ZETA (N iterations), comparison, recording, and summary</li>
 *   <li>{@code assertDualEquivalent(S, String)} — convenience for inline {@code @Test} methods</li>
 * </ul>
 *
 * <p>Example (discovery test, ~50 LOC):
 * <pre>
 * &#64;Tag("performance")
 * public class Jsl2PsmDiscoveryComparisonTest
 *     extends AbstractDualComparisonTest&lt;JslDslModel, PsmModel&gt; {
 *
 *     &#64;Override protected String getModuleName() { return "jsl2psm"; }
 *     &#64;Override protected JslDslModel parseSource(ExternalModelConfig c) { ... }
 *     &#64;Override protected PsmModel executeEtl(JslDslModel m) { ... }
 *     &#64;Override protected PsmModel executeZeta(JslDslModel m) { ... }
 *     &#64;Override protected Resource getResource(PsmModel m) { ... }
 * }
 * </pre>
 *
 * @param <S> source model type (e.g., JslDslModel)
 * @param <T> target model type (e.g., PsmModel, UiModel)
 */
@Slf4j
public abstract class AbstractDualComparisonTest<S, T> extends AbstractExternalModelTest {

    // ========================================================================
    // Abstract methods (subclass MUST implement)
    // ========================================================================

    /**
     * Parses a source model from the given configuration.
     *
     * <p>Called multiple times per model (for warmup, ETL, ZETA) to ensure
     * each transformation gets a fresh, unmutated source.
     *
     * @param config the model configuration (directory, parameters)
     * @return the parsed source model
     * @throws Exception if parsing fails
     */
    protected abstract S parseSource(ExternalModelConfig config) throws Exception;

    /**
     * Executes the ETL (Epsilon) transformation on the source model.
     *
     * @param source the source model (freshly parsed)
     * @param config the model configuration (null when called from assertDualEquivalent)
     * @return the transformation result
     * @throws Exception if the transformation fails
     */
    protected abstract T executeEtl(S source, ExternalModelConfig config) throws Exception;

    /**
     * Executes the ZETA (Java) transformation on the source model.
     *
     * @param source the source model (freshly parsed)
     * @param config the model configuration (null when called from assertDualEquivalent)
     * @return the transformation result
     * @throws Exception if the transformation fails
     */
    protected abstract T executeZeta(S source, ExternalModelConfig config) throws Exception;

    /**
     * Extracts the primary EMF Resource from a transformation result.
     *
     * <p>The returned resource is passed to {@link ModelComparator#compare(Resource, Resource)}
     * which performs identity-based root element matching.
     *
     * @param model the transformation result
     * @return the EMF resource to compare
     */
    protected abstract Resource getResource(T model);

    /**
     * Returns the module name used in summary reporting and JSON output.
     *
     * @return module name (e.g., "jsl2psm", "jsl2ui")
     */
    protected abstract String getModuleName();

    // ========================================================================
    // Optional overrides
    // ========================================================================

    /**
     * Label for element counts in reports. Default: "elements".
     */
    protected String getOutputLabel() {
        return "elements";
    }

    /**
     * Properties file for model discovery. Default: "external-model-tests.properties".
     */
    protected String getPropertiesFile() {
        return "external-model-tests.properties";
    }

    /**
     * Comparison mode for ModelComparator. Default: from system property or STRICT.
     */
    protected ModelComparator.ComparisonMode getComparisonMode() {
        return ModelComparator.getConfiguredMode();
    }

    /**
     * Whether to fail the test on comparison mismatch. Default: false (log only).
     *
     * <p>Override to return {@code true} for inline dual tests that should fail on diff.
     */
    protected boolean shouldFailOnDiff() {
        return false;
    }

    // ========================================================================
    // @TestFactory — external model discovery + dual comparison
    // ========================================================================

    /**
     * Discovers external models and runs dual comparison for each.
     *
     * <p>Orchestration per model:
     * <ol>
     *   <li>Parse source (validate model loads)</li>
     *   <li>Optional warmup: parse + ETL + parse + ZETA (discarded)</li>
     *   <li>ETL: fresh parse → timed execution → TimedResult</li>
     *   <li>ZETA: fresh parse → timed execution (N iterations avg) → TimedResult</li>
     *   <li>Compare via ModelComparator</li>
     *   <li>Record result for summary</li>
     * </ol>
     *
     * @return collection of dynamic tests, one per model plus a summary test
     */
    @TestFactory
    Collection<DynamicTest> compareExternalModels() {
        Stream<ExternalModelConfig> configs = loadModelConfigs(getPropertiesFile(), getClass());
        List<ExternalModelConfig> configList = configs.collect(Collectors.toList());

        Assumptions.assumeTrue(!configList.isEmpty(),
                "No models found (configure " + getPropertiesFile() + " or set discovery basedir)");

        clearResults();
        log.info("Dual comparison: {} models discovered, mode={}", configList.size(), getComparisonMode());

        List<DynamicTest> tests = new ArrayList<>();
        for (ExternalModelConfig config : configList) {
            tests.add(DynamicTest.dynamicTest(config.modelName(), () -> testModel(config)));
        }

        tests.add(DynamicTest.dynamicTest("== Summary ==", () -> {
            printSummary(getModuleName());
            writeJsonResults(getModuleName(), Path.of("target"));
        }));

        return tests;
    }

    private void testModel(ExternalModelConfig config) throws Exception {
        if (!checkModelExists(config)) {
            return;
        }
        logTestHeader(getModuleName(), config);

        // Validate source can be parsed
        S validationSource;
        try {
            validationSource = parseSource(config);
        } catch (Exception e) {
            Assumptions.assumeTrue(false,
                    "Source parse failed for " + config.modelName() + ": " + e.getMessage());
            return;
        }

        // Warmup
        if (config.isWarmupEnabled()) {
            log.info("--- Warmup ---");
            try {
                executeEtl(parseSource(config), config);
                executeZeta(parseSource(config), config);
            } catch (Exception e) {
                log.warn("Warmup failed (continuing): {}", e.getMessage());
            }
            log.info("Warmup complete");
        }

        // ETL: timed
        S etlSource = parseSource(config);
        PerformanceMeasurement.TimedResult<T> etl = PerformanceMeasurement.measure(() -> executeEtl(etlSource, config));

        // ZETA: timed (N iterations)
        int iterations = config.getIterations();
        PerformanceMeasurement.TimedResult<T> zeta = PerformanceMeasurement.measureAvg(
                () -> executeZeta(parseSource(config), config), iterations);

        // Count elements
        Resource etlResource = getResource(etl.result());
        Resource zetaResource = getResource(zeta.result());
        int etlElements = countElements(etlResource.getResourceSet());
        int zetaElements = countElements(zetaResource.getResourceSet());

        // Log timing
        double speedup = zeta.durationMs() > 0 ? (double) etl.durationMs() / zeta.durationMs() : 0;
        log.info("ETL:  {}ms, {} {}", etl.durationMs(), etlElements, getOutputLabel());
        log.info("ZETA: {}ms avg ({} iteration{}), {} {}",
                zeta.durationMs(), iterations, iterations > 1 ? "s" : "",
                zetaElements, getOutputLabel());
        log.info("Speedup: {}", String.format("%.2fx", speedup));

        // Compare
        if (etlElements == 0 && zetaElements == 0) {
            log.info("Both models empty — EQUIVALENT");
            recordResult(config.modelName(), etl.durationMs(), zeta.durationMs(),
                    etlElements, zetaElements, getOutputLabel(), "EQUIVALENT", 0);
            return;
        }

        ModelComparator.ComparisonResult result = ModelComparator.compare(
                etlResource, zetaResource, getComparisonMode());

        if (result.isEquivalent()) {
            log.info("EQUIVALENT: ETL and ZETA models match ({})", getComparisonMode());
            recordResult(config.modelName(), etl.durationMs(), zeta.durationMs(),
                    etlElements, zetaElements, getOutputLabel(), "EQUIVALENT", 0);
            // XMI ID comparison runs after structural equivalence is confirmed.
            // Uses flexible matching (exactMatch=false) because ETL and Zeta rule names
            // legitimately differ (e.g., ETL "BoundOperationAnnotation" vs Zeta "BoundAnnotationForBoundTransferOperation").
            ModelComparator.assertXmiIdsEquivalent(etlResource, zetaResource, false);
        } else {
            log.warn("{} differences found", result.getDifferenceCount());
            result.getDifferenceList().stream().limit(10)
                    .forEach(d -> log.warn("  {}", d.describe()));
            recordResult(config.modelName(), etl.durationMs(), zeta.durationMs(),
                    etlElements, zetaElements, getOutputLabel(), "FAILED", result.getDifferenceCount());

            if (shouldFailOnDiff()) {
                fail(getModuleName() + " comparison failed for " + config.modelName()
                        + ": " + result.getDifferenceCount() + " differences\n"
                        + result.getSummary());
            }
        }
    }

    // ========================================================================
    // Convenience methods for inline @Test methods
    // ========================================================================

    /**
     * Runs both ETL and ZETA on the given source and asserts the results are equivalent.
     *
     * <p>Use this in inline {@code @Test} methods where the source model is already parsed.
     * Note: if the transformation mutates the source, use
     * {@link #assertDualEquivalent(Supplier, String)} instead.
     *
     * @param source the pre-parsed source model
     * @param testName name for error reporting
     * @throws Exception if transformation or comparison fails
     */
    protected void assertDualEquivalent(S source, String testName) throws Exception {
        T etlResult = executeEtl(source, null);
        T zetaResult = executeZeta(source, null);

        Resource etlResource = getResource(etlResult);
        Resource zetaResource = getResource(zetaResult);

        ModelComparator.ComparisonResult result = ModelComparator.compare(
                etlResource, zetaResource, getComparisonMode());

        if (!result.isEquivalent()) {
            fail(getModuleName() + " comparison failed for " + testName
                    + ": " + result.getDifferenceCount() + " differences\n"
                    + result.getDetailedReport());
        }
    }

    /**
     * Runs both ETL and ZETA with fresh sources from the factory and asserts equivalence.
     *
     * <p>Use this when the source model may be mutated by the transformation.
     * The factory is called once for ETL and once for ZETA.
     *
     * @param sourceFactory supplier that creates a fresh source model
     * @param testName name for error reporting
     * @throws Exception if transformation or comparison fails
     */
    protected void assertDualEquivalent(Supplier<S> sourceFactory, String testName) throws Exception {
        T etlResult = executeEtl(sourceFactory.get(), null);
        T zetaResult = executeZeta(sourceFactory.get(), null);

        Resource etlResource = getResource(etlResult);
        Resource zetaResource = getResource(zetaResult);

        ModelComparator.ComparisonResult result = ModelComparator.compare(
                etlResource, zetaResource, getComparisonMode());

        if (!result.isEquivalent()) {
            fail(getModuleName() + " comparison failed for " + testName
                    + ": " + result.getDifferenceCount() + " differences\n"
                    + result.getDetailedReport());
        }
    }
}
