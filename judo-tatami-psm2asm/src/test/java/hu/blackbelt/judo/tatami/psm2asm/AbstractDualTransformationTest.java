package hu.blackbelt.judo.tatami.psm2asm;

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

import hu.blackbelt.judo.meta.asm.runtime.AsmModel;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Abstract base class for dual transformation tests.
 * <p>
 * This class provides infrastructure for running tests with both ETL and Zeta
 * transformation engines. Subclasses should implement the abstract methods
 * to provide the specific transformation logic.
 * </p>
 * 
 * <h2>Configuration</h2>
 * The comparison behavior can be configured via system properties:
 * <ul>
 *   <li>{@code judo.test.comparison.enabled} - Enable/disable comparison (default: true)</li>
 *   <li>{@code judo.test.comparison.mode} - Comparison mode: STRICT, STRUCTURAL, LENIENT (default: STRUCTURAL)</li>
 *   <li>{@code judo.test.comparison.maxDifferences} - Max differences to report (default: 50)</li>
 *   <li>{@code judo.test.comparison.reportFile} - Output file for diff report (optional)</li>
 * </ul>
 * 
 * <p>Example usage:</p>
 * <pre>{@code
 * public class MyTransformationTest extends AbstractDualTransformationTest {
 *     
 *     @Override
 *     protected PsmModel createSourceModel() {
 *         return new Demo().fullDemo();
 *     }
 *     
 *     @Override
 *     protected AsmModel runEtlTransformation(PsmModel source) {
 *         // Run ETL transformation
 *     }
 *     
 *     @Override
 *     protected AsmModel runZetaTransformation(PsmModel source) {
 *         // Run Zeta transformation
 *     }
 * }
 * }</pre>
 */
@Slf4j
public abstract class AbstractDualTransformationTest {

    // Store transformation results for comparison
    private AsmModel etlResult;
    private AsmModel zetaResult;

    /**
     * Creates the source PSM model for transformation.
     * Subclasses must implement this to provide the test model.
     *
     * @return the source PSM model
     * @throws Exception if model creation fails
     */
    protected abstract PsmModel createSourceModel() throws Exception;

    /**
     * Runs the ETL transformation on the given source model.
     *
     * @param source the source PSM model
     * @return the resulting ASM model
     * @throws Exception if transformation fails
     */
    protected abstract AsmModel runEtlTransformation(PsmModel source) throws Exception;

    /**
     * Runs the Zeta transformation on the given source model.
     *
     * @param source the source PSM model
     * @return the resulting ASM model
     * @throws Exception if transformation fails
     */
    protected abstract AsmModel runZetaTransformation(PsmModel source) throws Exception;

    /**
     * Performs additional verification on the transformation result.
     * Subclasses can override this to add custom assertions.
     *
     * @param result the transformation result
     * @param transformationType the type of transformation that was run
     */
    protected void verifyResult(AsmModel result, TransformationType transformationType) {
        assertNotNull(result, "Transformation result should not be null");
        assertNotNull(result.getResourceSet(), "Result resource set should not be null");
    }

    /**
     * Gets the comparison mode to use for model comparison.
     * Subclasses can override this to specify a different default mode.
     *
     * @return the comparison mode
     */
    protected ModelComparator.ComparisonMode getComparisonMode() {
        return ModelComparator.getConfiguredMode();
    }

    /**
     * Stores the transformation result for later comparison.
     *
     * @param type the transformation type
     * @param result the transformation result
     */
    protected void storeTransformationResult(TransformationType type, AsmModel result) {
        switch (type) {
            case ETL -> etlResult = result;
            case ZETA -> zetaResult = result;
        }
    }

    /**
     * Gets the stored ETL transformation result.
     *
     * @return the ETL result, or null if not yet executed
     */
    protected AsmModel getStoredEtlResult() {
        return etlResult;
    }

    /**
     * Gets the stored Zeta transformation result.
     *
     * @return the Zeta result, or null if not yet executed
     */
    protected AsmModel getStoredZetaResult() {
        return zetaResult;
    }

    /**
     * Compares the stored ETL and Zeta results.
     * Both transformations must have been executed before calling this method.
     *
     * @throws AssertionError if the results are not available or not equivalent
     */
    protected void compareStoredResults() {
        assertNotNull(etlResult, "ETL result not available - run ETL transformation first");
        assertNotNull(zetaResult, "Zeta result not available - run Zeta transformation first");
        compareModels(etlResult, zetaResult);
    }

    /**
     * Parameterized test that runs the transformation with both ETL and Zeta engines.
     *
     * @param type the transformation type to use
     * @throws Exception if transformation fails
     */
    @ParameterizedTest(name = "Transformation with {0}")
    @EnumSource(TransformationType.class)
    void testTransformation(TransformationType type) throws Exception {
        PsmModel source = createSourceModel();
        assertNotNull(source, "Source model should not be null");
        assertTrue(source.isValid(), "Source model should be valid");

        AsmModel result = switch (type) {
            case ETL -> runEtlTransformation(source);
            case ZETA -> runZetaTransformation(source);
        };

        storeTransformationResult(type, result);
        verifyResult(result, type);
    }

    /**
     * Tests that both ETL and Zeta transformations produce equivalent results.
     * This test runs both transformations and compares the output models.
     * <p>
     * The comparison uses the configured comparison mode (default: STRUCTURAL).
     * Use {@code -Djudo.test.comparison.mode=STRICT} for exact matching.
     *
     * @throws Exception if transformation or comparison fails
     */
    @Test
    void testDualEquivalence() throws Exception {
        if (!ModelComparator.isComparisonEnabled()) {
            log.info("Model comparison is disabled via system property");
            return;
        }

        PsmModel source = createSourceModel();
        assertNotNull(source, "Source model should not be null");
        assertTrue(source.isValid(), "Source model should be valid");

        log.info("Running ETL transformation...");
        long etlStart = System.currentTimeMillis();
        AsmModel etlResult = runEtlTransformation(source);
        long etlDuration = System.currentTimeMillis() - etlStart;
        log.info("ETL transformation completed in {}ms", etlDuration);

        log.info("Running Zeta transformation...");
        long zetaStart = System.currentTimeMillis();
        AsmModel zetaResult = runZetaTransformation(source);
        long zetaDuration = System.currentTimeMillis() - zetaStart;
        log.info("Zeta transformation completed in {}ms", zetaDuration);

        log.info("Comparing results with mode: {}", getComparisonMode());
        compareModels(etlResult, zetaResult);
        log.info("Models are equivalent");
    }

    /**
     * Compares two ASM models for equivalence.
     * Subclasses can override this to customize comparison logic.
     *
     * @param etlResult the result from ETL transformation
     * @param zetaResult the result from Zeta transformation
     */
    protected void compareModels(AsmModel etlResult, AsmModel zetaResult) {
        assertNotNull(etlResult, "ETL result should not be null");
        assertNotNull(zetaResult, "Zeta result should not be null");

        // Compare the root packages
        if (!etlResult.getResourceSet().getResources().isEmpty() && 
            !zetaResult.getResourceSet().getResources().isEmpty()) {
            
            ModelComparator.assertEquivalent(
                    etlResult.getResourceSet().getResources().get(0),
                    zetaResult.getResourceSet().getResources().get(0),
                    getComparisonMode()
            );
        }
    }

    /**
     * Asserts that two models are structurally equivalent.
     * Utility method for use in subclass tests.
     *
     * @param expected the expected model
     * @param actual the actual model
     */
    protected void assertModelsEquivalent(EObject expected, EObject actual) {
        ModelComparator.assertEquivalent(expected, actual, getComparisonMode());
    }

    /**
     * Asserts that two models are structurally equivalent with a specific mode.
     * Utility method for use in subclass tests.
     *
     * @param expected the expected model
     * @param actual the actual model
     * @param mode the comparison mode to use
     */
    protected void assertModelsEquivalent(EObject expected, EObject actual, 
                                          ModelComparator.ComparisonMode mode) {
        ModelComparator.assertEquivalent(expected, actual, mode);
    }

    /**
     * Utility method to get the root element from an ASM model.
     *
     * @param model the ASM model
     * @return the root EObject, or null if not found
     */
    protected EObject getRootElement(AsmModel model) {
        if (model != null && 
            model.getResourceSet() != null && 
            !model.getResourceSet().getResources().isEmpty() &&
            !model.getResourceSet().getResources().get(0).getContents().isEmpty()) {
            return model.getResourceSet().getResources().get(0).getContents().get(0);
        }
        return null;
    }
}
