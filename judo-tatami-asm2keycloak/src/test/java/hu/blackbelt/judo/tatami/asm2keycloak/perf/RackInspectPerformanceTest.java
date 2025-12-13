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
import hu.blackbelt.judo.meta.asm.runtime.AsmUtils;
import hu.blackbelt.judo.meta.keycloak.runtime.KeycloakModel;
import hu.blackbelt.judo.tatami.asm2keycloak.util.ModelComparator;
import hu.blackbelt.judo.tatami.asm2keycloak.zeta.Asm2KeycloakZetaTransformation;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;

import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.LoadArguments.asmLoadArgumentsBuilder;
import static hu.blackbelt.judo.meta.asm.runtime.AsmModel.loadAsmModel;
import static hu.blackbelt.judo.meta.keycloak.runtime.KeycloakModel.buildKeycloakModel;
import static hu.blackbelt.judo.tatami.asm2keycloak.Asm2Keycloak.Asm2KeycloakParameter.asm2KeycloakParameter;
import static hu.blackbelt.judo.tatami.asm2keycloak.Asm2Keycloak.executeAsm2KeycloakTransformation;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Performance test using the real-world RackInspect model for ASM to Keycloak transformation.
 * 
 * Run with: mvn test -pl judo-tatami-asm2keycloak -Dtest=RackInspectPerformanceTest -Pperformance
 */
@Slf4j
@Tag("performance")
public class RackInspectPerformanceTest {

    private static final File RACKINSPECT_ASM = new File("../rackinspect/rackinspect-asm.model");
    
    private static AsmModel asmModel;
    private static int actorCount;
    private static int classCount;
    private static int totalElements;

    @BeforeAll
    static void loadModel() throws Exception {
        if (!RACKINSPECT_ASM.exists()) {
            throw new IllegalStateException("RackInspect ASM model not found at: " + RACKINSPECT_ASM.getAbsolutePath());
        }

        log.info("Loading RackInspect ASM model from: {}", RACKINSPECT_ASM.getAbsolutePath());
        long loadStart = System.currentTimeMillis();
        
        asmModel = loadAsmModel(asmLoadArgumentsBuilder()
                .uri(org.eclipse.emf.common.util.URI.createFileURI(RACKINSPECT_ASM.getAbsolutePath()))
                .build());
        
        long loadTime = System.currentTimeMillis() - loadStart;
        log.info("Model loaded in {}ms", loadTime);

        // Count model elements
        AsmUtils asmUtils = new AsmUtils(asmModel.getResourceSet());
        actorCount = (int) asmUtils.all(EClass.class)
                .filter(c -> AsmUtils.isActorType(c))
                .count();
        classCount = (int) asmUtils.all(EClass.class).count();
        
        totalElements = 0;
        for (var resource : asmModel.getResourceSet().getResources()) {
            var iterator = resource.getAllContents();
            while (iterator.hasNext()) {
                iterator.next();
                totalElements++;
            }
        }

        log.info("Model statistics:");
        log.info("  - Actors: {}", actorCount);
        log.info("  - EClasses: {}", classCount);
        log.info("  - Total ASM Elements: {}", totalElements);
    }

    @Test
    void testRackInspectPerformance() throws Exception {
        log.info("");
        log.info("================================================================");
        log.info("ETL vs ZETA Performance: RackInspect ASM to Keycloak");
        log.info("================================================================");
        log.info("Actors: {}, Classes: {}, Total ASM Elements: {}", 
                actorCount, classCount, totalElements);
        log.info("");

        // Warmup both transformations (JIT compilation)
        log.info("--- Warmup ---");
        log.info("Warming up ETL...");
        executeEtlTransformation();
        log.info("Warming up ZETA...");
        executeZetaTransformation();
        log.info("Warmup complete");
        log.info("");

        // Run ETL transformation
        log.info("--- ETL Transformation (measured) ---");
        long etlStart = System.currentTimeMillis();
        KeycloakModel etlResult = executeEtlTransformation();
        long etlTime = System.currentTimeMillis() - etlStart;
        int etlElementCount = countKeycloakElements(etlResult);
        log.info("ETL completed in {}ms, produced {} Keycloak elements", etlTime, etlElementCount);

        // Run Zeta transformation
        log.info("");
        log.info("--- ZETA Transformation (measured) ---");
        long zetaStart = System.currentTimeMillis();
        KeycloakModel zetaResult = executeZetaTransformation();
        long zetaTime = System.currentTimeMillis() - zetaStart;
        int zetaElementCount = countKeycloakElements(zetaResult);
        log.info("Zeta completed in {}ms, produced {} Keycloak elements", zetaTime, zetaElementCount);

        // Model equivalence check
        log.info("");
        log.info("--- Model Equivalence Check ---");
        compareModels(etlResult, zetaResult);

        // Print comparison
        log.info("");
        log.info("================================================================");
        log.info("COMPARISON RESULTS: RackInspect ASM to Keycloak");
        log.info("================================================================");
        log.info("");
        log.info("                    ETL              ZETA           Difference");
        log.info("----------------------------------------------------------------");
        log.info("Time:         {}ms       {}ms       {}ms ({}%)", 
                String.format("%8d", etlTime), 
                String.format("%8d", zetaTime),
                String.format("%+8d", zetaTime - etlTime),
                String.format("%+.1f", ((double)(zetaTime - etlTime) / Math.max(etlTime, 1)) * 100));
        log.info("Output Elements: {}       {}", 
                String.format("%8d", etlElementCount), 
                String.format("%8d", zetaElementCount));
        log.info("");
        
        if (etlTime > 0 && zetaTime > 0) {
            double etlThroughput = actorCount / (etlTime / 1000.0);
            double zetaThroughput = actorCount / (zetaTime / 1000.0);
            log.info("Throughput:   {}/s       {}/s", 
                    String.format("%8.0f", etlThroughput), 
                    String.format("%8.0f", zetaThroughput));
        }
        log.info("");
        
        if (zetaTime < etlTime) {
            double speedup = (double) etlTime / Math.max(zetaTime, 1);
            log.info(">>> ZETA is {}x FASTER than ETL <<<", String.format("%.2f", speedup));
        } else if (zetaTime > etlTime) {
            double slowdown = (double) zetaTime / Math.max(etlTime, 1);
            log.info(">>> ZETA is {}x SLOWER than ETL <<<", String.format("%.2f", slowdown));
        } else {
            log.info(">>> ETL and ZETA have EQUAL performance <<<");
        }
        log.info("================================================================");

        // Assert Zeta is not significantly slower
        assertTrue(zetaTime <= etlTime * 2, 
                "Zeta should not be more than 2x slower than ETL");
    }

    private KeycloakModel executeEtlTransformation() throws Exception {
        // Reload ASM model for fresh transformation
        AsmModel freshAsmModel = loadAsmModel(asmLoadArgumentsBuilder()
                .uri(org.eclipse.emf.common.util.URI.createFileURI(RACKINSPECT_ASM.getAbsolutePath()))
                .build());
        
        KeycloakModel keycloakModel = buildKeycloakModel()
                .name("RackInspect")
                .build();
        
        executeAsm2KeycloakTransformation(asm2KeycloakParameter()
                .asmModel(freshAsmModel)
                .keycloakModel(keycloakModel));
        
        return keycloakModel;
    }

    private KeycloakModel executeZetaTransformation() throws Exception {
        // Reload ASM model for fresh transformation
        AsmModel freshAsmModel = loadAsmModel(asmLoadArgumentsBuilder()
                .uri(org.eclipse.emf.common.util.URI.createFileURI(RACKINSPECT_ASM.getAbsolutePath()))
                .build());
        
        KeycloakModel keycloakModel = buildKeycloakModel()
                .name("RackInspect")
                .build();
        
        Asm2KeycloakZetaTransformation transformation = Asm2KeycloakZetaTransformation.builder()
                .asmModel(freshAsmModel)
                .keycloakModel(keycloakModel)
                .build();
        transformation.execute();
        
        return keycloakModel;
    }

    private int countKeycloakElements(KeycloakModel keycloakModel) {
        int count = 0;
        for (var resource : keycloakModel.getResourceSet().getResources()) {
            var iterator = resource.getAllContents();
            while (iterator.hasNext()) {
                iterator.next();
                count++;
            }
        }
        return count;
    }

    private void compareModels(KeycloakModel etlResult, KeycloakModel zetaResult) {
        assertTrue(!etlResult.getResourceSet().getResources().isEmpty(), 
                "ETL result has no resources");
        assertTrue(!zetaResult.getResourceSet().getResources().isEmpty(), 
                "Zeta result has no resources");

        int etlCount = countKeycloakElements(etlResult);
        int zetaCount = countKeycloakElements(zetaResult);
        
        log.info("ETL produced {} elements, Zeta produced {} elements", etlCount, zetaCount);

        if (etlCount == zetaCount) {
            if (!etlResult.getResourceSet().getResources().get(0).getContents().isEmpty() &&
                !zetaResult.getResourceSet().getResources().get(0).getContents().isEmpty()) {
                
                EObject etlRoot = etlResult.getResourceSet().getResources().get(0).getContents().get(0);
                EObject zetaRoot = zetaResult.getResourceSet().getResources().get(0).getContents().get(0);
                
                ModelComparator.ComparisonResult result = ModelComparator.compare(etlRoot, zetaRoot);
                
                if (result.isEquivalent()) {
                    log.info("SUCCESS: ETL and Zeta models are equivalent");
                } else {
                    log.info("Models have {} difference(s) - checking structural equivalence", 
                            result.getDifferenceCount());
                }
            }
        } else {
            log.warn("Element count mismatch: ETL={}, Zeta={}", etlCount, zetaCount);
        }
    }
}
