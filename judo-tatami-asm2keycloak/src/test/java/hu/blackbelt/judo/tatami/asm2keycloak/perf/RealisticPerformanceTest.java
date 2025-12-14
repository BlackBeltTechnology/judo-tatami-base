package hu.blackbelt.judo.tatami.asm2keycloak.perf;

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
import hu.blackbelt.judo.meta.keycloak.runtime.KeycloakModel;
import hu.blackbelt.judo.tatami.asm2keycloak.util.ModelComparator;
import hu.blackbelt.judo.tatami.asm2keycloak.zeta.Asm2KeycloakZetaTransformation;
import hu.blackbelt.judo.tatami.core.TransformationMode;
import hu.blackbelt.judo.tatami.test.RealisticAsmModelGenerator;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static hu.blackbelt.judo.meta.keycloak.runtime.KeycloakModel.buildKeycloakModel;
import static hu.blackbelt.judo.tatami.asm2keycloak.Asm2Keycloak.Asm2KeycloakParameter.asm2KeycloakParameter;
import static hu.blackbelt.judo.tatami.asm2keycloak.Asm2Keycloak.executeAsm2KeycloakTransformation;

/**
 * Realistic performance test that generates a synthetic ASM model with actors
 * similar to the RackInspect real-world model using the shared RealisticAsmModelGenerator.
 *
 * Run with: mvn test -pl judo-tatami-asm2keycloak -Dtest=RealisticPerformanceTest -Pperformance
 */
@Slf4j
@Tag("performance")
public class RealisticPerformanceTest {

    private final RealisticAsmModelGenerator generator = new RealisticAsmModelGenerator();

    @Test
    void testRealisticModelPerformance() throws Exception {
        runPerformanceTest(70, 5, "RackInspect-like");
    }

    @Test
    void testSmallRealisticModel() throws Exception {
        runPerformanceTest(20, 3, "Small");
    }

    @Test
    void testLargeRealisticModel() throws Exception {
        runPerformanceTest(100, 10, "Large");
    }

    private void runPerformanceTest(int entityCount, int actorCount, String testName) throws Exception {
        log.info("");
        log.info("================================================================");
        log.info("Realistic Performance Test (Asm2Keycloak): {} ({} entities, {} actors)", testName, entityCount, actorCount);
        log.info("================================================================");

        // Generate model using shared generator with actors
        long genStart = System.currentTimeMillis();
        AsmModel asmModel = generator.generateWithActors(entityCount, actorCount);
        long genTime = System.currentTimeMillis() - genStart;

        int classifierCount = RealisticAsmModelGenerator.countClassifiers(asmModel);

        log.info("Model generated in {}ms", genTime);
        log.info("  Entity classes: {}", entityCount);
        log.info("  Actor classes: {}", actorCount);
        log.info("  Total classifiers: {}", classifierCount);
        log.info("");

        // Warmup
        log.info("--- Warmup ---");
        log.info("Warming up ETL...");
        executeTransformation(asmModel, TransformationMode.ETL);
        log.info("Warming up ZETA...");
        executeTransformation(asmModel, TransformationMode.ZETA);
        log.info("Warmup complete");
        log.info("");

        // ETL measurement
        log.info("--- ETL Transformation (measured) ---");
        long etlStart = System.currentTimeMillis();
        KeycloakModel etlResult = executeTransformation(asmModel, TransformationMode.ETL);
        long etlTime = System.currentTimeMillis() - etlStart;
        int etlElements = countElements(etlResult);
        log.info("ETL completed in {}ms, produced {} elements", etlTime, etlElements);

        // Zeta measurement
        log.info("");
        log.info("--- ZETA Transformation (measured) ---");
        long zetaStart = System.currentTimeMillis();
        KeycloakModel zetaResult = executeTransformation(asmModel, TransformationMode.ZETA);
        long zetaTime = System.currentTimeMillis() - zetaStart;
        int zetaElements = countElements(zetaResult);
        log.info("Zeta completed in {}ms, produced {} elements", zetaTime, zetaElements);

        // Model equivalence check
        log.info("");
        log.info("--- Model Equivalence Check ---");
        compareModels(etlResult, zetaResult);

        // Results
        printResults(testName, entityCount, actorCount, classifierCount, etlTime, zetaTime, etlElements, zetaElements);
    }

    private KeycloakModel executeTransformation(AsmModel asmModel, TransformationMode mode) throws Exception {
        KeycloakModel keycloakModel = buildKeycloakModel()
                .name("RealisticTest")
                .build();

        if (mode.isZeta()) {
            Asm2KeycloakZetaTransformation transformation = Asm2KeycloakZetaTransformation.builder()
                    .asmModel(asmModel)
                    .keycloakModel(keycloakModel)
                    .build();
            transformation.execute();
        } else {
            executeAsm2KeycloakTransformation(asm2KeycloakParameter()
                    .asmModel(asmModel)
                    .keycloakModel(keycloakModel));
        }

        return keycloakModel;
    }

    private int countElements(KeycloakModel model) {
        int count = 0;
        for (var resource : model.getResourceSet().getResources()) {
            var iterator = resource.getAllContents();
            while (iterator.hasNext()) {
                iterator.next();
                count++;
            }
        }
        return count;
    }

    private void compareModels(KeycloakModel etlResult, KeycloakModel zetaResult) {
        int etlElements = countElements(etlResult);
        int zetaElements = countElements(zetaResult);

        if (etlElements != zetaElements) {
            log.warn("Element count mismatch: ETL={}, Zeta={}", etlElements, zetaElements);
        } else {
            log.info("Element count: {} (both match)", etlElements);
        }

        // Structural comparison
        if (!etlResult.getResourceSet().getResources().isEmpty() &&
            !zetaResult.getResourceSet().getResources().isEmpty() &&
            !etlResult.getResourceSet().getResources().get(0).getContents().isEmpty() &&
            !zetaResult.getResourceSet().getResources().get(0).getContents().isEmpty()) {

            EObject etlRoot = etlResult.getResourceSet().getResources().get(0).getContents().get(0);
            EObject zetaRoot = zetaResult.getResourceSet().getResources().get(0).getContents().get(0);

            ModelComparator.ComparisonResult result = ModelComparator.compare(etlRoot, zetaRoot);

            if (result.isEquivalent()) {
                log.info("SUCCESS: ETL and Zeta models are structurally equivalent");
            } else {
                log.warn("Models have {} difference(s)", result.getDifferenceList().size());
                int count = 0;
                for (ModelComparator.Difference diff : result.getDifferenceList()) {
                    if (count++ >= 10) {
                        log.warn("  ... and {} more differences", result.getDifferenceList().size() - 10);
                        break;
                    }
                    log.warn("  - {}", diff);
                }
            }
        }
    }

    private void printResults(String testName, int entityCount, int actorCount, int classifierCount,
                              long etlTime, long zetaTime, int etlElements, int zetaElements) {
        log.info("");
        log.info("================================================================");
        log.info("RESULTS: {} ({} entities, {} actors, {} classifiers)", testName, entityCount, actorCount, classifierCount);
        log.info("================================================================");
        log.info("");
        log.info("                    ETL              ZETA           Difference");
        log.info("----------------------------------------------------------------");
        log.info("Time:         {}ms       {}ms       {}ms ({}%)",
                String.format("%8d", etlTime),
                String.format("%8d", zetaTime),
                String.format("%+8d", zetaTime - etlTime),
                String.format("%+.1f", ((double) (zetaTime - etlTime) / Math.max(etlTime, 1)) * 100));
        log.info("Elements:     {}       {}",
                String.format("%8d", etlElements),
                String.format("%8d", zetaElements));
        log.info("");

        double etlThroughput = classifierCount / (Math.max(etlTime, 1) / 1000.0);
        double zetaThroughput = classifierCount / (Math.max(zetaTime, 1) / 1000.0);
        log.info("Throughput:   {}/s       {}/s",
                String.format("%8.0f", etlThroughput),
                String.format("%8.0f", zetaThroughput));
        log.info("");

        if (zetaTime < etlTime) {
            double speedup = (double) etlTime / zetaTime;
            log.info(">>> ZETA is {}x FASTER than ETL <<<", String.format("%.2f", speedup));
        } else if (zetaTime > etlTime) {
            double slowdown = (double) zetaTime / etlTime;
            log.info(">>> ZETA is {}x SLOWER than ETL <<<", String.format("%.2f", slowdown));
        } else {
            log.info(">>> ETL and ZETA have EQUAL performance <<<");
        }
        log.info("================================================================");
    }
}
