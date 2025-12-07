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

        verifyResult(result, type);
    }

    /**
     * Tests that both ETL and Zeta transformations produce equivalent results.
     * This test runs both transformations and compares the output models.
     *
     * @throws Exception if transformation or comparison fails
     */
    @Test
    void testDualEquivalence() throws Exception {
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

        log.info("Comparing results...");
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
                    zetaResult.getResourceSet().getResources().get(0)
            );
        }
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
