package hu.blackbelt.judo.tatami.psm2measure.zeta;

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

import hu.blackbelt.judo.meta.measure.runtime.MeasureModel;
import hu.blackbelt.judo.meta.psm.PsmUtils;
import hu.blackbelt.judo.meta.psm.runtime.PsmModel;
import hu.blackbelt.judo.tatami.psm2measure.zeta.rules.MeasureRules;
import hu.blackbelt.judo.tatami.psm2measure.zeta.rules.UnitRules;
import hu.blackbelt.judo.zeta.common.ExtensionMethodRegistry;
import hu.blackbelt.judo.zeta.common.ModelProvider;
import hu.blackbelt.judo.zeta.transformation.core.TransformationContext;
import hu.blackbelt.judo.zeta.transformation.core.TransformationExecutor;
import hu.blackbelt.judo.zeta.transformation.core.TransformationRegistry;
import hu.blackbelt.judo.zeta.transformation.core.TransformationResult;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.ResourceSet;

import java.util.*;

/**
 * PSM to Measure transformation using Zeta framework's TransformationRegistry and TransformationExecutor.
 * <p>
 * This class uses the Zeta framework's declarative rule-based transformation approach:
 * <ul>
 *   <li>Rules are declared in separate classes with @TransformRule annotations</li>
 *   <li>TransformationRegistry scans and registers all rules</li>
 *   <li>TransformationExecutor executes rules in proper order</li>
 *   <li>Supports parallel execution for large models</li>
 * </ul>
 * </p>
 * <p>
 * Rule classes:
 * <ul>
 *   <li>{@link MeasureRules} - measure.etl rules (CreateMeasure, CreateBaseMeasure, CreateDerivedMeasure)</li>
 *   <li>{@link UnitRules} - unit.etl rules (CreateUnit, CreateDurationUnit)</li>
 * </ul>
 * </p>
 */
@Slf4j
public class Psm2MeasureZetaTransformation {

    private final PsmModel psmModel;
    private final MeasureModel measureModel;

    @Builder
    public Psm2MeasureZetaTransformation(
            @NonNull PsmModel psmModel,
            @NonNull MeasureModel measureModel) {
        this.psmModel = psmModel;
        this.measureModel = measureModel;
    }

    /**
     * Execute the transformation using TransformationExecutor.
     *
     * @return map of source to target element mappings (trace)
     */
    public Map<EObject, List<EObject>> execute() {
        log.info("Starting PSM to Measure Zeta transformation");
        long startTime = System.currentTimeMillis();

        // Create registry and register all rule classes
        TransformationRegistry registry = createRegistry();

        // Create transformation context
        TransformationContext context = createContext(registry);

        // Create executor with sequential execution
        // Note: parallel(true) causes ConcurrentModificationException on EMF collections
        // which are not thread-safe when adding elements to Resource.getContents()
        TransformationExecutor executor = TransformationExecutor.builder()
                .registry(registry)
                .context(context)
                .parallel(false)
                .build();

        // Execute transformation
        log.debug("Starting executor.transform()");
        TransformationResult result = executor.transform();
        log.debug("Finished executor.transform()");

        long duration = System.currentTimeMillis() - startTime;
        log.info("PSM to Measure Zeta transformation completed in {}ms", duration);

        // Build trace from context's element resolution cache
        return buildTraceResult(context);
    }

    /**
     * Creates and configures the TransformationRegistry with all rule classes.
     * Rule execution order is determined by registration order.
     */
    private TransformationRegistry createRegistry() {
        TransformationRegistry registry = new TransformationRegistry();

        // Phase 1: Measure rules (base measures before derived)
        registry.register(MeasureRules.class);

        // Phase 2: Unit rules (units reference measures)
        registry.register(UnitRules.class);

        log.debug("Registered {} rule classes with TransformationRegistry", 2);
        return registry;
    }

    /**
     * Creates and configures the TransformationContext.
     */
    private TransformationContext createContext(TransformationRegistry registry) {
        ResourceSet sourceResourceSet = psmModel.getResourceSet();
        ResourceSet targetResourceSet = measureModel.getResourceSet();

        // Create model provider
        ModelProvider modelProvider = new Psm2MeasureModelProvider(psmModel);

        // Create extension method registry
        ExtensionMethodRegistry extensionRegistry = new ExtensionMethodRegistry();

        // Create context
        TransformationContext context = new TransformationContext(
                modelProvider,
                sourceResourceSet,
                targetResourceSet,
                extensionRegistry
        );

        // Configure context
        context.setTransformationRegistry(registry);

        // Register resources with aliases
        // "source" is the default alias used by @Transform annotations
        context.registerResource("source", sourceResourceSet);
        context.registerResource("psm", sourceResourceSet);
        context.registerResource("target", targetResourceSet);
        context.registerResource("measure", targetResourceSet);

        // Store utilities in context attributes for rules to access
        PsmUtils psmUtils = new PsmUtils(sourceResourceSet);
        context.setAttribute("psmUtils", psmUtils);
        context.setAttribute("measureResource", measureModel.getResource());

        return context;
    }

    /**
     * Builds the trace result from the transformation context.
     */
    private Map<EObject, List<EObject>> buildTraceResult(TransformationContext context) {
        Map<EObject, List<EObject>> result = new HashMap<>();
        // The TransformationContext's ElementResolutionCache contains the mappings
        // For now, return empty map - the actual trace is maintained by the context
        return result;
    }

    /**
     * ModelProvider implementation for PSM to Measure transformation.
     */
    private static class Psm2MeasureModelProvider implements ModelProvider {
        private final PsmModel psmModel;
        private final PsmUtils psmUtils;

        public Psm2MeasureModelProvider(PsmModel psmModel) {
            this.psmModel = psmModel;
            this.psmUtils = new PsmUtils(psmModel.getResourceSet());
        }

        @Override
        public <T extends EObject> Collection<T> getAllContents(ResourceSet resourceSet, Class<T> type) {
            return psmUtils.all(resourceSet, type).toList();
        }

        @Override
        public String getName(EObject element) {
            if (element instanceof hu.blackbelt.judo.meta.psm.namespace.NamedElement) {
                return ((hu.blackbelt.judo.meta.psm.namespace.NamedElement) element).getName();
            }
            return ModelProvider.super.getName(element);
        }

        @Override
        public String getTypeName(EObject element) {
            return element.eClass().getName();
        }
    }
}
