package hu.blackbelt.judo.tatami.asm2query;

/*-
 * #%L
 * JUDO Runtime Core :: Parent
 * %%
 * Copyright (C) 2018 - 2022 BlackBelt Technology
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
import hu.blackbelt.judo.meta.expression.runtime.ExpressionModel;
import hu.blackbelt.judo.meta.measure.runtime.MeasureModel;
import hu.blackbelt.judo.meta.expression.DataExpression;
import hu.blackbelt.judo.meta.expression.Expression;
import hu.blackbelt.judo.meta.expression.ReferenceExpression;
import hu.blackbelt.judo.meta.expression.adapters.asm.AsmModelAdapter;
import hu.blackbelt.judo.meta.expression.builder.jql.JqlExpressionBuilder;
import hu.blackbelt.judo.meta.query.*;
import hu.blackbelt.judo.meta.query.runtime.QueryUtils;
import hu.blackbelt.judo.meta.query.runtime.QueryModel;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.emf.common.notify.Notifier;
import org.eclipse.emf.common.util.UniqueEList;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.builder.EAttributeBuilder;
import org.eclipse.emf.ecore.util.builder.EClassBuilder;

import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static hu.blackbelt.judo.meta.query.runtime.QueryUtils.getNextSubSelectAlias;
import static hu.blackbelt.judo.meta.query.util.builder.QueryBuilders.*;

/**
 * Logical query factory for ASM mapped transfer object types.
 */
@Slf4j
public class Asm2Query {

    /**
     * Version of the {@link QueryModelInfo} contract written by this builder and accepted by {@link QueryIndex}.
     */
    public static final int FORMAT_VERSION = 1;

    public static final String TABLE_ALIAS_FORMAT = "t{0,number,00}";

    public static final String EXPOSED_ALIAS = "__exposed";
    public static final String DATA_ALIAS = "__data";

    /**
     * Parameters of the query model build.
     */
    @Builder(builderMethodName = "asm2QueryParameter")
    public static class Asm2QueryParameter {
        /**
         * ASM resource set.
         */
        @NonNull
        ResourceSet asmResourceSet;

        /**
         * Measure resource set (an empty one is used when absent).
         */
        ResourceSet measureResourceSet;

        /**
         * Expression resource set the bindings are collected from. The whole resource set is indexed once.
         */
        @NonNull
        ResourceSet expressionResourceSet;

        /**
         * Name of the model, the query resource gets the logical URI {@code <modelName>-query.model}.
         */
        @NonNull
        String modelName;

        /**
         * Version of the model (optional).
         */
        String modelVersion;

        /**
         * Custom join definitions of derived transfer relations (optional).
         */
        Map<EReference, CustomJoinDefinition> customJoinDefinitions;

        /**
         * Leave the expression model untouched (default {@code false}).
         *
         * <p>The converters create the iterator variable of a collection expression that has none
         * ({@code IterableExpression.createIterator}), which attaches it to the expression model. With {@code true} those
         * iterators are removed again when the build is done, so the model equals the one before the build. The
         * persisting workflow stage ({@link Asm2QueryWork}) sets it: the expression model of the run is saved next to the query
         * model and must not depend on the query stage. A runtime build keeps the historic behaviour.</p>
         */
        boolean preserveExpressionModel;
    }

    /**
     * Build the logical query model.
     *
     * @param parameter parameters
     * @return query model, index and the expression converter of the build
     */
    public static Asm2QueryResult executeAsm2Query(final Asm2QueryParameter.Asm2QueryParameterBuilder parameter) {
        return executeAsm2Query(parameter.build());
    }

    /**
     * Build the logical query model.
     *
     * @param parameter parameters
     * @return query model, index and the expression converter of the build
     */
    public static Asm2QueryResult executeAsm2Query(final Asm2QueryParameter parameter) {
        final Asm2Query asm2Query = new Asm2Query(parameter);
        return new Asm2QueryResult(asm2Query.queryModel, asm2Query.index, asm2Query.converter, asm2Query.customJoinDefinitions);
    }

    /**
     * Build the logical query model of ASM, measure (optional) and expression models.
     *
     * @param asmModel        ASM model, its name is the model name of the query model
     * @param measureModel    measure model, optional
     * @param expressionModel expression model extracted from the ASM model
     * @return query model, index and the expression converter of the build
     */
    public static Asm2QueryResult executeAsm2Query(final AsmModel asmModel, final MeasureModel measureModel, final ExpressionModel expressionModel) {
        return executeAsm2Query(Asm2QueryParameter.asm2QueryParameter()
                .asmResourceSet(asmModel.getResourceSet())
                .measureResourceSet(measureModel != null ? measureModel.getResourceSet() : null)
                .expressionResourceSet(expressionModel.getResourceSet())
                .modelName(asmModel.getName())
                .modelVersion(asmModel.getVersion()));
    }

    private final ResourceSet asmResourceSet;

    private final TransferObjectTypeBindingsCollector transferObjectTypeBindingsCollector;

    private final AtomicInteger nextSourceIndex = new AtomicInteger(0);

    private final AtomicInteger nextTargetIndex = new AtomicInteger(0);

    private final AsmUtils asmUtils;

    private final DataExpressionConverter converter;
    private final FeatureFactory featureFactory;
    private final JoinFactory joinFactory;

    private final Map<EClass, Select> transferObjectQueries = new ConcurrentHashMap<>(); // key: ASM mapped transfer object type
    private final Map<EReference, SubSelect> navigationQueries = new ConcurrentHashMap<>(); // key: ASM exposed graph
    private final Map<EAttribute, SubSelect> dataQueries = new ConcurrentHashMap<>(); // key: ASM exposed graph

    private final Map<EReference, CustomJoinDefinition> customJoinDefinitions;

    // concurrent: filled from parallel streams
    private final Set<EReference> orderedTransferRelations = ConcurrentHashMap.newKeySet();

    private final QueryModel queryModel;

    private final List<EObject> createdQueryObjects = new CopyOnWriteArrayList<>();

    private final QueryIndex index;

    private Asm2Query(final Asm2QueryParameter parameter) {
        this.asmResourceSet = parameter.asmResourceSet;
        this.customJoinDefinitions = parameter.customJoinDefinitions == null ? new ConcurrentHashMap<>() : parameter.customJoinDefinitions;

        // logical URI: <name>-query.model, never query:in-memory
        final QueryModel.QueryModelBuilder queryModelBuilder = QueryModel.buildQueryModel().name(parameter.modelName);
        if (parameter.modelVersion != null) {
            queryModelBuilder.version(parameter.modelVersion);
        }
        queryModel = queryModelBuilder.build();

        asmUtils = new AsmUtils(asmResourceSet);
        warmUpEClassCaches();

        transferObjectTypeBindingsCollector = new TransferObjectTypeBindingsCollector(asmResourceSet, parameter.expressionResourceSet);

        converter = new DataExpressionConverter(asmResourceSet, parameter.measureResourceSet);
        joinFactory = converter.getJoinFactory();
        featureFactory = converter.getFeatureFactory();

        if (parameter.preserveExpressionModel) {
            featureFactory.startTrackingCreatedIterators();
        }
        try {
            createQueries();
            createNavigations();
        } finally {
            featureFactory.releaseCreatedIterators();
        }

        for (EObject object : createdQueryObjects) {
            queryModel.addContent(object);
        }

        index = new QueryIndex(asmUtils,
                transferObjectQueries,
                navigationQueries,
                dataQueries,
                orderedTransferRelations,
                transferObjectTypeBindingsCollector.getStaticEntityAttributes(),
                transferObjectTypeBindingsCollector.getStaticEntityReferences(),
                transferObjectTypeBindingsCollector.getEntityTypeExpressionsMap(),
                nextSourceIndex,
                nextTargetIndex);

        queryModel.addContent(createQueryModelInfo(parameter.modelName));

        if (!queryModel.isValid()) {
            log.error("Invalid query model: {}", queryModel.getDiagnosticsAsString());
            throw new IllegalStateException("Invalid query model");
        }
    }

    /**
     * EMF computes the feature tables of an {@code EClass} lazily and without synchronization. The build reads them from parallel
     * streams, so a table that another thread is still computing shows up as an {@code NullPointerException} in
     * {@code EClassImpl.getFeatureCount} (seen under CPU contention). Compute them once here, single-threaded.
     */
    private void warmUpEClassCaches() {
        asmUtils.all(EClass.class).forEach(eClass -> {
            eClass.getEAllStructuralFeatures();
            eClass.getEAllAttributes();
            eClass.getEAllReferences();
            eClass.getEAllContainments();
            eClass.getEAllSuperTypes();
            eClass.getEAllOperations();
            eClass.getEIDAttribute();
            eClass.getFeatureCount();
        });
    }

    /**
     * Create the persisted index root: keys, ordered relations, static sets, entity expression entries (empty ones
     * included), counters, format version and model name. Stamps (generator, digests) are set on save. Entries are
     * sorted by ASM name to get a deterministic order.
     */
    private QueryModelInfo createQueryModelInfo(final String modelName) {
        final QueryModelInfo info = newQueryModelInfoBuilder()
                .withFormatVersion(FORMAT_VERSION)
                .withModelName(modelName)
                .withNextSourceIndex(index.getNextSourceIndex().get())
                .withNextTargetIndex(index.getNextTargetIndex().get())
                .build();

        index.getQueryTypes().stream().sorted(Comparator.comparing(AsmUtils::getClassifierFQName))
                .forEach(t -> info.getQueries().add(newQueryEntryBuilder()
                        .withType(t)
                        .withSelect(index.getQuery(t).orElseThrow(() -> new IllegalStateException("Query not found: " + AsmUtils.getClassifierFQName(t))))
                        .build()));
        index.getNavigationReferences().stream().sorted(Comparator.comparing(AsmUtils::getReferenceFQName))
                .forEach(r -> info.getNavigations().add(newNavigationEntryBuilder()
                        .withReference(r)
                        .withSubSelect(index.getNavigation(r).orElseThrow(() -> new IllegalStateException("Navigation not found: " + AsmUtils.getReferenceFQName(r))))
                        .build()));
        index.getDataQueryAttributes().stream().sorted(Comparator.comparing(AsmUtils::getAttributeFQName))
                .forEach(a -> info.getDataQueries().add(newDataQueryEntryBuilder()
                        .withAttribute(a)
                        .withSubSelect(index.getDataQuery(a).orElseThrow(() -> new IllegalStateException("Data query not found: " + AsmUtils.getAttributeFQName(a))))
                        .build()));

        index.getOrderedTransferRelations().stream().sorted(Comparator.comparing(AsmUtils::getReferenceFQName))
                .forEach(r -> info.getOrderedTransferRelations().add(r));
        index.getStaticEntityAttributes().stream().sorted(Comparator.comparing(AsmUtils::getAttributeFQName))
                .forEach(a -> info.getStaticEntityAttributes().add(a));
        index.getStaticEntityReferences().stream().sorted(Comparator.comparing(AsmUtils::getReferenceFQName))
                .forEach(r -> info.getStaticEntityReferences().add(r));

        index.getEntityTypeExpressionsMap().entrySet().stream()
                .sorted(Comparator.comparing(e -> AsmUtils.getClassifierFQName(e.getKey())))
                .forEach(e -> {
                    final EntityTypeExpressionsEntry entry = newEntityTypeExpressionsEntryBuilder()
                            .withEntityType(e.getKey())
                            .build();
                    e.getValue().getGetterAttributeExpressions().entrySet().stream()
                            .sorted(Comparator.comparing(g -> AsmUtils.getAttributeFQName(g.getKey())))
                            .forEach(g -> entry.getGetterAttributes().add(newGetterAttributeEntryBuilder()
                                    .withAttribute(g.getKey())
                                    .withExpression(g.getValue())
                                    .build()));
                    e.getValue().getGetterReferenceExpressions().entrySet().stream()
                            .sorted(Comparator.comparing(g -> AsmUtils.getReferenceFQName(g.getKey())))
                            .forEach(g -> entry.getGetterReferences().add(newGetterReferenceEntryBuilder()
                                    .withReference(g.getKey())
                                    .withExpression(g.getValue())
                                    .build()));
                    info.getEntityTypeExpressions().add(entry);
                });

        return info;
    }

    private Optional<Select> getQuery(final EClass transferObjectType) {
        return Optional.ofNullable(transferObjectQueries.get(transferObjectType));
    }

    private Map<EClass, EntityTypeExpressions> getEntityTypeExpressionsMap() {
        return transferObjectTypeBindingsCollector.getEntityTypeExpressionsMap();
    }

    private void createLogicalQueryMainTarget(EClass mappedTransferObjectType, Map<EClass, MappedTransferObjectTypeBindings> processedMappedTransferObjectTypeBindings) {
        final Optional<MappedTransferObjectTypeBindings> node = transferObjectTypeBindingsCollector.getTransferObjectGraph(mappedTransferObjectType, processedMappedTransferObjectTypeBindings);

        // mapped transfer object types that have no expression tree are ignored
        if (node.isPresent()) {
            if (log.isTraceEnabled()) {
                log.trace("Creating logical query skeleton for transfer object type: {}", AsmUtils.getClassifierFQName(mappedTransferObjectType));
            }

            // create logical query base
            final Select select = newSelectBuilder()
                    .withFrom(node.get().getEntityType())
                    .withAlias(MessageFormat.format(TABLE_ALIAS_FORMAT, nextSourceIndex.incrementAndGet()))
                    .withMainTarget(newTargetBuilder()
                            .withType(mappedTransferObjectType)
                            .withIndex(nextTargetIndex.incrementAndGet())
                            .build())
                    .build();
            select.getTargets().add(select.getMainTarget());
            select.getMainTarget().setContainerWithIdFeature(select, true);

            createdQueryObjects.add(select);

            transferObjectQueries.put(mappedTransferObjectType, select);
        } else {
            log.error("No root node of expression tree found for mapped transfer object type: {}, query not created", AsmUtils.getClassifierFQName(mappedTransferObjectType));
        }
    }

    private void createLogicalQueryWithoutReferences(EClass mappedTransferObjectType, Map<EClass, MappedTransferObjectTypeBindings> processedMappedTransferObjectTypeBindings) {
        final Optional<MappedTransferObjectTypeBindings> node = transferObjectTypeBindingsCollector.getTransferObjectGraph(mappedTransferObjectType, processedMappedTransferObjectTypeBindings);

        if (node.isPresent()) {
            if (log.isTraceEnabled()) {
                log.trace("Setup logical query for transfer object type: {}", AsmUtils.getClassifierFQName(mappedTransferObjectType));
            }

            final Select select = transferObjectQueries.get(mappedTransferObjectType);
            final Context context = Context.builder()
                    .createdQueryObjects(createdQueryObjects)
                    .node(select)
                    .sourceCounter(nextSourceIndex)
                    .targetCounter(nextTargetIndex)
                    .variables(Collections.singletonMap(JqlExpressionBuilder.SELF_NAME, select)).build();
            addAttributes(node.get(), context, select.getMainTarget()); // add all attributes to logical query
            addFilter(node.get(), context);
        } else {
            log.error("No root node of expression tree found for mapped transfer object type: {}, query not created", AsmUtils.getClassifierFQName(mappedTransferObjectType));
        }
    }

    private void addQueryReferences(EClass mappedTransferObjectType, Map<EClass, MappedTransferObjectTypeBindings> processedMappedTransferObjectTypeBindings) {
        final Optional<MappedTransferObjectTypeBindings> node = transferObjectTypeBindingsCollector.getTransferObjectGraph(mappedTransferObjectType, processedMappedTransferObjectTypeBindings);

        if (node.isPresent()) {
            if (log.isTraceEnabled()) {
                log.trace("Adding references of transfer object type: {}", AsmUtils.getClassifierFQName(mappedTransferObjectType));
            }
            final Select select = transferObjectQueries.get(mappedTransferObjectType);
            addReferences(node.get(), Context.builder()
                            .createdQueryObjects(createdQueryObjects)
                            .node(select)
                            .sourceCounter(nextSourceIndex)
                            .targetCounter(nextTargetIndex)
                            .variables(Collections.singletonMap(JqlExpressionBuilder.SELF_NAME, select)).build(),
                    select.getMainTarget(),
                    Collections.emptyList()); // add all references to logical query
        } else {
            log.error("No root node of expression tree found for mapped transfer object type: {}, reference not added", AsmUtils.getClassifierFQName(mappedTransferObjectType));
        }
    }

        /**
         * Create logical queries for transfer object types:
         * - create logical queries for all mapped transfer object types,
         * - add references (as JOINs or SUBSELECTs)
         */
    private void createQueries() {
        final Map<EClass, MappedTransferObjectTypeBindings> processedMappedTransferObjectTypeBindings = new ConcurrentHashMap<>();

        final Set<EClass> mappedEntities = new HashSet<>(getAsmElement(EClass.class)
                .filter(c -> asmUtils.getMappedEntityType(c).isPresent()).collect(Collectors.toSet()));

        // create logical query templates (main target only!)
        mappedEntities.parallelStream()
                .forEach(mappedTransferObjectType -> {
                    createLogicalQueryMainTarget(mappedTransferObjectType, processedMappedTransferObjectTypeBindings);
                });

        // create logical query (without references)
        mappedEntities.parallelStream()
                .filter(c -> asmUtils.getMappedEntityType(c).isPresent())
                .forEach(mappedTransferObjectType -> {
                    createLogicalQueryWithoutReferences(mappedTransferObjectType, processedMappedTransferObjectTypeBindings);
                });

        // add references (both single and multiple)
        mappedEntities.parallelStream()
                .filter(c -> asmUtils.getMappedEntityType(c).isPresent())
                .forEach(mappedTransferObjectType -> {
                    addQueryReferences(mappedTransferObjectType, processedMappedTransferObjectTypeBindings);
                });
    }

    private void createNavigations() {
        Set<EAttribute> attributesToProcess = getAsmElement(EAttribute.class)
                .filter(a -> a.isDerived() && !AsmUtils.isEntityType(a.getEContainingClass()) && isStaticAttribute(a)).collect(Collectors.toSet());

        attributesToProcess.parallelStream()
                .forEach(attribute -> {
                    final DataExpression dataExpression;
                    if (asmUtils.isMappedTransferObjectType(attribute.getEContainingClass())) {
                        if (attribute.getEContainingClass().getEAllAttributes().stream()
                                .anyMatch(a -> Objects.equals(attribute.getName(), AsmUtils.getExtensionAnnotationValue(a, "default", false).orElse("-")))) {
                            dataExpression = asmUtils.getMappedAttribute(attribute).map(mappedAttribute ->
                                    getEntityTypeExpressionsMap().get(mappedAttribute.getEContainingClass()).getGetterAttributeExpressions().get(mappedAttribute))
                                    .orElse(null);
                        } else {
                            // attribute is not used as default
                            return;
                        }
                    } else {
                        final Optional<UnmappedTransferObjectTypeBindings> source = transferObjectTypeBindingsCollector.getTransferObjectBindings(attribute.getEContainingClass());
                        if (!source.isPresent()) {
                            log.warn("Expression binding not found: {}", AsmUtils.getAttributeFQName(attribute));
                            return;
                        }

                        dataExpression = source.get().getDataExpressions().get(attribute);
                    }

                    if (dataExpression == null) {
                        log.error("Data expression is not defined for attribute: {}", AsmUtils.getAttributeFQName(attribute));
                        return;
                    }

                    final EAttribute resultAttribute = EAttributeBuilder.create()
                            .withName(attribute.getName())
                            .withEType(attribute.getEAttributeType())
                            .build();
                    final EClass resultType = EClassBuilder.create()
                            .withName("__ResultType")
                            .withEStructuralFeatures(resultAttribute)
                            .build();

                    final Select select = newSelectBuilder()
                            .withAlias(DATA_ALIAS)
                            .build();
                    final Target mainTarget = newTargetBuilder()
                            .withType(resultType)
                            .withIndex(nextTargetIndex.incrementAndGet())
                            .withNode(select)
                            .build();
                    final Feature feature = featureFactory.convert(dataExpression, Context.builder()
                                    .createdQueryObjects(createdQueryObjects)
                                    .node(select)
                                    .sourceCounter(nextSourceIndex)
                                    .targetCounter(nextTargetIndex)
                                    .variables(Collections.emptyMap())
                                    .build(),
                            newFeatureTargetMappingBuilder()
                                    .withTarget(mainTarget)
                                    .withTargetAttribute(resultAttribute)
                                    .build());

                    useSelect(select)
                            .withFeatures(feature)
                            .withTargets(mainTarget)
                            .withMainTarget(mainTarget)
                            .build();

                    final SubSelect subSelect = newSubSelectBuilder()
                            .withSelect(select)
                            .withEmbeddedSelect(select)
                            .withAlias(EXPOSED_ALIAS)
                            .build();

                    createdQueryObjects.add(subSelect);
                    createdQueryObjects.add(resultType);

                    dataQueries.put(attribute, subSelect);
                });

        Set<EReference> referencesToProcess = getAsmElement(EReference.class)
                .filter(r -> asmUtils.isMappedTransferObjectType(r.getEReferenceType()) && r.isDerived() && !AsmUtils.isEntityType(r.getEContainingClass()) && isStaticReference(r)).collect(Collectors.toSet());

        referencesToProcess.parallelStream()
                .forEach(navigation -> {
                    final ReferenceExpression selector;
                    if (asmUtils.isMappedTransferObjectType(navigation.getEContainingClass())) {
                        if (navigation.getEContainingClass().getEAllReferences().stream()
                                .anyMatch(a -> Objects.equals(navigation.getName(), AsmUtils.getExtensionAnnotationValue(a, "default", false).orElse("-")))) {
                            selector = asmUtils.getMappedReference(navigation).map(mappedReference ->
                                    getEntityTypeExpressionsMap().get(mappedReference.getEContainingClass()).getGetterReferenceExpressions().get(mappedReference))
                                    .orElse(null);
                        } else {
                            // reference is not used as default
                            return;
                        }
                    } else {
                        final Optional<UnmappedTransferObjectTypeBindings> source = transferObjectTypeBindingsCollector.getTransferObjectBindings(navigation.getEContainingClass());
                        if (!source.isPresent()) {
                            log.warn("Expression binding not found: {}", AsmUtils.getReferenceFQName(navigation));
                            return;
                        }

                        selector = source.get().getNavigationExpressions().get(navigation);
                    }

                    if (selector == null) {
                        log.error("Selector is not defined for navigation: {}", AsmUtils.getReferenceFQName(navigation));
                        return;
                    }

                    final SubSelect subSelect = newSubSelectBuilder()
                            .withSelect(getQuery(navigation.getEReferenceType()).get())
                            .withAlias(EXPOSED_ALIAS)
                            .withTransferRelation(navigation)
                            .build();

                    final JoinFactory.PathEnds pathEnds = joinFactory.convertNavigationToJoins(Context.builder()
                                    .createdQueryObjects(createdQueryObjects)
                                    .sourceCounter(nextSourceIndex)
                                    .targetCounter(nextTargetIndex)
                                    .variables(Collections.emptyMap()).build(),
                            subSelect,
                            selector, false);

                    if (log.isTraceEnabled()) {
                        log.trace("Static navigation: " + subSelect.getNavigationJoins().stream().map(j -> j.toString()).collect(Collectors.joining(", ")));
                    }

                    if (pathEnds.isOrdered()) {
                        orderedTransferRelations.add(navigation);
                    }

                    createdQueryObjects.add(subSelect);

                    navigationQueries.put(navigation, subSelect);
                });
    }

    private Feature dataExpressionToFeature(final DataExpression dataExpression, final Context context, final Target target, final EAttribute targetAttribute) {
        return converter.dataExpressionToFeature(dataExpression, context, target, targetAttribute);
    }

    /**
     * Add all attributes of a given expression tree node to logical query, returning result set in a target.
     *
     * @param mappedTransferObjectTypeBindings node of expression tree
     * @param context                          context
     * @param target                           projection of node added to logical query
     */
    private void addAttributes(final MappedTransferObjectTypeBindings mappedTransferObjectTypeBindings, final Context context, final Target target) {
        if (!AsmUtils.equals(mappedTransferObjectTypeBindings.getTransferObjectType(), target.getType())) {
            throw new IllegalArgumentException("Mismatching target type");
        }
        log.trace("  - adding attributes of {} to {}", context.getVariables().get(JqlExpressionBuilder.SELF_NAME).getAlias(), target);
        mappedTransferObjectTypeBindings.getGetterAttributeExpressions().entrySet().stream()
                .filter(e -> e.getKey().getEContainingClass().getEAllStructuralFeatures().stream()
                        .noneMatch(f -> AsmUtils.getExtensionAnnotationValue(f, "default", false)
                                .filter(d -> Objects.equals(d, e.getKey().getName()))
                                .isPresent()))
                .forEach(e -> dataExpressionToFeature(e.getValue(), context, target, e.getKey()));
    }

    private void addFilter(final MappedTransferObjectTypeBindings mappedTransferObjectTypeBindings, final Context context) {
        final Node self = context.getVariables().get(JqlExpressionBuilder.SELF_NAME);
        if (mappedTransferObjectTypeBindings.getFilter() != null) {
            log.trace("  - adding filter of {}", self.getAlias());

            final Feature feature = dataExpressionToFeature(mappedTransferObjectTypeBindings.getFilter(), context, null, null);
            self.getFilters().add(newFilterBuilder()
                    .withAlias("f" + self.getAlias())
                    .withFeature(feature)
                    .build());
        }
    }

    /**
     * Add all references (both single and multiple) of a given expression tree node to logical query.
     *
     * @param mappedTransferObjectTypeBindings node of expression tree
     * @param context                          context
     */
    private void addReferences(final MappedTransferObjectTypeBindings mappedTransferObjectTypeBindings, final Context context, final Target target, final List<EReference> referenceChain) {
        final List<EReference> subSelectReferences = new UniqueEList<>();

        mappedTransferObjectTypeBindings.getReferences().entrySet().stream()
                .filter(c -> mappedTransferObjectTypeBindings.getGetterReferenceExpressions().containsKey(c.getKey()))
                .forEach(c -> {
                    EReference reference = c.getKey();

                    final List<EReference> newReferenceChain = new ArrayList<>();
                    newReferenceChain.addAll(referenceChain);
                    newReferenceChain.add(reference);

                    if (log.isTraceEnabled()) {
                        log.trace(pad(referenceChain.size()) + "- adding SUBQUERY reference: {}", newReferenceChain.stream().map(r -> r.getName()).collect(Collectors.joining(".")));
                    }

                    // create SUBSELECT for multiple reference including logical query of the reference
                    final SubSelect subSelect = newSubSelectBuilder()
                            .withAlias(getNextSubSelectAlias(nextSourceIndex))
                            .withSelect(transferObjectQueries.get(reference.getEReferenceType()))
                            .withTransferRelation(reference)
                            .build();

                    if (customJoinDefinitions.get(reference) != null && reference.isDerived()) {
                        final EClass entityType = asmUtils.getMappedEntityType(reference.getEReferenceType()).orElseThrow(() -> new IllegalStateException("Entity type not found"));

                        final CustomJoin customJoin = newCustomJoinBuilder()
                                .withAlias("custom" + context.getSourceCounter().incrementAndGet())
                                .withPartner(context.getNode())
                                .withTransferRelation(reference)
                                .withType(entityType)
                                .withNavigationSql(customJoinDefinitions.get(reference).getNavigationSql())
                                .build();
                        if (customJoinDefinitions.get(reference).getSourceIdParameterName() != null) {
                            customJoin.setSourceIdParameter(customJoinDefinitions.get(reference).getSourceIdParameterName());
                        }
                        if (customJoinDefinitions.get(reference).getSourceIdSetParameterName() != null) {
                            customJoin.setSourceIdSetParameter(customJoinDefinitions.get(reference).getSourceIdSetParameterName());
                        }
                        subSelect.setBase(context.getNode());
                        subSelect.getJoins().add(customJoin);
                        subSelect.setPartner(customJoin);
                    } else {
                        // add additional JOINs that specifies the role (navigation expressions of navigation properties)
                        final ReferenceExpression referenceExpression = mappedTransferObjectTypeBindings.getGetterReferenceExpressions().get(reference);
                        final JoinFactory.PathEnds pathEnds = joinFactory.convertNavigationToJoins(context, subSelect, referenceExpression, false);
                        if (pathEnds.isOrdered()) {
                            orderedTransferRelations.add(reference);
                        }
                    }

                    final boolean includedByJoin = !reference.isMany() &&
                            !referenceChain.contains(reference) &&
                            !subSelect.getNavigationJoins().stream().anyMatch(j -> j instanceof SubSelectJoin && isCircularAggregation(referenceChain, reference)) && // TODO - check condition is necessary
                            AsmUtils.equals(subSelect.getBase(), context.getNode());
                    subSelect.setExcluding(includedByJoin);

                    if (!includedByJoin) {
                        // add main target to the reference to target list of base select
                        final Target subSelectTarget = subSelect.getSelect().getMainTarget();
                        target.getReferencedTargets().add(newReferencedTargetBuilder()
                                .withReference(reference)
                                .withTarget(subSelectTarget)
                                .build());
                        subSelectReferences.add(reference);
                    }

                    context.getVariables().get(JqlExpressionBuilder.SELF_NAME).getSubSelects().add(subSelect);
                });

        // single references are added as JOIN
        mappedTransferObjectTypeBindings.getReferences().entrySet().stream()
                .filter(c -> AsmUtils.isEmbedded(c.getKey()) && !subSelectReferences.contains(c.getKey()) && mappedTransferObjectTypeBindings.getGetterReferenceExpressions().containsKey(c.getKey()))
                .forEach(c -> {
                    EReference reference = c.getKey();
                    MappedTransferObjectTypeBindings transferObjectTypeBindings = c.getValue();

                    final List<EReference> newReferenceChain = new ArrayList<>();
                    newReferenceChain.addAll(referenceChain);
                    newReferenceChain.add(reference);

                    if (log.isTraceEnabled()) {
                        log.trace(pad(referenceChain.size()) + "- adding JOIN reference: {}", newReferenceChain.stream().map(r -> r.getName()).collect(Collectors.joining(".")));
                    }

                    // get logical query of single reference, several elements will be copied from it
                    final Select joinedSelect = transferObjectQueries.get(reference.getEReferenceType());

                    final Node self = context.getVariables().get(JqlExpressionBuilder.SELF_NAME);
                    final Node nextNode;

                    if (customJoinDefinitions.get(reference) != null && reference.isDerived()) {
                        final EClass entityType = asmUtils.getMappedEntityType(reference.getEReferenceType()).orElseThrow(() -> new IllegalStateException("Entity type not found"));

                        final CustomJoin customJoin = newCustomJoinBuilder()
                                .withAlias("custom" + context.getSourceCounter().incrementAndGet())
                                .withPartner(context.getNode())
                                .withTransferRelation(reference)
                                .withType(entityType)
                                .withNavigationSql(customJoinDefinitions.get(reference).getNavigationSql())
                                .build();
                        if (customJoinDefinitions.get(reference).getSourceIdParameterName() != null) {
                            customJoin.setSourceIdParameter(customJoinDefinitions.get(reference).getSourceIdParameterName());
                        }
                        if (customJoinDefinitions.get(reference).getSourceIdSetParameterName() != null) {
                            customJoin.setSourceIdSetParameter(customJoinDefinitions.get(reference).getSourceIdSetParameterName());
                        }
                        self.getJoins().add(customJoin);

                        nextNode = newCastJoinBuilder()
                                .withAlias(QueryUtils.getNextJoinAlias(context.getSourceCounter()))
                                .withPartner(customJoin)
                                .withType(entityType)
                                .build();
                        self.getJoins().add((Join) nextNode);
                    } else {
                        // add additional JOINs that specifies the role (navigation expressions of navigation properties)
                        final ReferenceExpression referenceExpression = mappedTransferObjectTypeBindings.getGetterReferenceExpressions().get(reference);
                        final JoinFactory.PathEnds pathEnds = joinFactory.convertNavigationToJoins(context, self, referenceExpression, false);
                        if (pathEnds.isOrdered()) {
                            orderedTransferRelations.add(reference);
                        }
                        nextNode = pathEnds.getPartner();
                    }

                    final Target joinedTarget = newTargetBuilder()
                            .withType(joinedSelect.getMainTarget().getType())
                            .withIndex(nextTargetIndex.incrementAndGet())
                            .build(); // set target alias (excluded from SQL)
                    joinedTarget.setContainerWithIdFeature(nextNode, true);

                    if (self instanceof Select) {
                        ((Select) self).getTargets().add(joinedTarget);
                    } else if (self instanceof Join) {
                        ((Select) ((Join) self).getBase()).getTargets().add(joinedTarget);
                    } else {
                        throw new IllegalStateException("Invalid variable");
                    }

                    // add main target of the reference to target list of base select
                    target.getReferencedTargets().add(newReferencedTargetBuilder()
                            .withReference(reference)
                            .withTarget(joinedTarget)
                            .build());

                    addReferences(transferObjectTypeBindings, context.clone(JqlExpressionBuilder.SELF_NAME, nextNode), joinedTarget, newReferenceChain);
                    addAttributes(transferObjectTypeBindings, context.clone(JqlExpressionBuilder.SELF_NAME, nextNode), joinedTarget); // add joined attributes to logical query
                    addFilter(transferObjectTypeBindings, context.clone(JqlExpressionBuilder.SELF_NAME, nextNode));
                });
    }

    private boolean isStaticReference(EReference reference) {
        return asmUtils.getMappedReference(reference)
                .map(r -> Boolean.TRUE.equals(transferObjectTypeBindingsCollector.isStaticReference(r)))
                .orElse(true); // static=true returned for transfer object relations without binding
    }

    private boolean isStaticAttribute(EAttribute attribute) {
        return asmUtils.getMappedAttribute(attribute)
                .map(a -> Boolean.TRUE.equals(transferObjectTypeBindingsCollector.isStaticAttribute(a)))
                .orElse(true); // static=true returned for transfer attributes without binding
    }

    private boolean isCircularAggregation(final List<EReference> references, final EReference reference) {
        final List<EClass> sourceTypes = new UniqueEList<>();
        sourceTypes.addAll(references.stream()
                .map(r -> r.getEContainingClass())
                .collect(Collectors.toList()));
        sourceTypes.add(reference.getEContainingClass());

        return isCircularAggregation(sourceTypes, new UniqueEList<>(), Collections.singletonList(reference));
    }

    private boolean isCircularAggregation(final List<EClass> sourceTypes, final List<EClass> checkedTypes, final List<EReference> references) {
        if (references.stream()
                .map(r -> r.getEReferenceType())
                .anyMatch(t -> sourceTypes.contains(t))) {
            return true;
        }

        final List<EClass> nextTypes = new UniqueEList<>();
        nextTypes.addAll(references.stream()
                .filter(r -> !checkedTypes.contains(r.getEReferenceType()))
                .map(r -> r.getEReferenceType())
                .collect(Collectors.toList()));

        final List<EReference> nextReferences = new UniqueEList<>();
        nextReferences.addAll(nextTypes.stream()
                .flatMap(c -> c.getEAllReferences().stream()
                        .filter(r -> AsmUtils.isEmbedded(r)))
                .collect(Collectors.toList()));
        checkedTypes.addAll(nextTypes);

        if (nextReferences.isEmpty()) {
            return false;
        } else {
            return isCircularAggregation(sourceTypes, checkedTypes, nextReferences);
        }
    }

    /**
     * Get elements with a given type of ASM metamodel.
     *
     * @param clazz class of element
     * @param <T>   element type
     * @return stream of elements
     */
    @SuppressWarnings("unchecked")
    <T> Stream<T> getAsmElement(final Class<T> clazz) {
        final Iterable<Notifier> asmContents = asmResourceSet::getAllContents;
        return StreamSupport.stream(asmContents.spliterator(), false)
                .filter(e -> clazz.isAssignableFrom(e.getClass())).map(e -> (T) e);
    }

    private static String pad(int level) {
        return StringUtils.leftPad("", level * 2, " ");
    }
}
