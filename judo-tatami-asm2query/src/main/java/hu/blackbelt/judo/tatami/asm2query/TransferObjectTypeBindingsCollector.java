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

import hu.blackbelt.judo.meta.asm.runtime.AsmUtils;
import hu.blackbelt.judo.meta.expression.*;
import hu.blackbelt.judo.meta.expression.binding.AttributeBinding;
import hu.blackbelt.judo.meta.expression.binding.AttributeBindingRole;
import hu.blackbelt.judo.meta.expression.binding.FilterBinding;
import hu.blackbelt.judo.meta.expression.binding.ReferenceBinding;
import hu.blackbelt.judo.meta.expression.binding.ReferenceBindingRole;
import hu.blackbelt.judo.meta.expression.runtime.ExpressionEvaluator;
import hu.blackbelt.judo.meta.expression.variable.Variable;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.common.notify.Notifier;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.ResourceSet;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.util.function.Function.identity;

/**
 * Expression builder loops over mapped transfer object types and creates expression trees based on primitive (attribute, data property) and reference (relation, navigation property) typed elements.
 */
@Slf4j
public class TransferObjectTypeBindingsCollector {

    /**
     * Resource set of expression model.
     */
    private final ResourceSet expressionResourceSet;

    /**
     * ASM utils.
     */
    private final AsmUtils asmUtils;

    private ExpressionEvaluator expressionEvaluator;

    private final Map<EClass, EntityTypeExpressions> entityTypeExpressionsMap = new ConcurrentHashMap<>();
    private final Map<EAttribute, List<Variable>> staticFlagOfAttributes = new ConcurrentHashMap<>();
    private final Map<EReference, List<Variable>> staticFlagOfReferences = new ConcurrentHashMap<>();

    /**
     * Binding index: binding class -&gt; type FQN ({@code namespace::name}) -&gt; elements in {@code getAllContents()} order.
     */
    private final Map<Class<?>, Map<String, List<EObject>>> bindingIndex;

    public TransferObjectTypeBindingsCollector(final ResourceSet asmResourceSet, final ResourceSet expressionResourceSet) {
        this.expressionResourceSet = expressionResourceSet;
        asmUtils = new AsmUtils(asmResourceSet);

        expressionEvaluator = new ExpressionEvaluator();
        if (!expressionResourceSet.getResources().isEmpty()) {
            expressionEvaluator.init(expressionResourceSet.getResources().get(0).getContents().stream()
                    .filter(c -> c instanceof Expression).map(c -> (Expression) c)
                    .collect(Collectors.toList()));
        }

        entityTypeExpressionsMap.putAll(asmUtils.all(EClass.class)
                .filter(c -> AsmUtils.isEntityType(c))
                .collect(Collectors.toMap(identity(), c -> EntityTypeExpressions.builder().entityType(c).build())));

        bindingIndex = buildBindingIndex();
    }

    public Map<EClass, EntityTypeExpressions> getEntityTypeExpressionsMap() {
        return Collections.unmodifiableMap(entityTypeExpressionsMap);
    }

    public Optional<UnmappedTransferObjectTypeBindings> getTransferObjectBindings(final EClass unmappedTransferObjectType) {
        if (!AsmUtils.isEntityType(unmappedTransferObjectType) && !asmUtils.isMappedTransferObjectType(unmappedTransferObjectType)) {
            final UnmappedTransferObjectTypeBindings bindings = UnmappedTransferObjectTypeBindings.builder()
                    .unmappedTransferObjectType(unmappedTransferObjectType)
                    .build();

            if (log.isTraceEnabled()) {
                log.trace("Collecting all attributes of unmapped transfer object type: {}", AsmUtils.getClassifierFQName(unmappedTransferObjectType));
            }
            unmappedTransferObjectType.getEAllAttributes().stream()
                    .filter(a -> !AsmUtils.isEntityType(a.getEContainingClass()) && !asmUtils.isMappedTransferObjectType(a.getEContainingClass()) && a.isDerived())
                    .forEach(attribute -> {
                        log.trace("  - attribute: {}", AsmUtils.getAttributeFQName(attribute));
                        getBindings(AttributeBinding.class, AsmUtils.getClassifierFQName(unmappedTransferObjectType).replace(".", "::")).stream()
                                .filter(b -> Objects.equals(unmappedTransferObjectType.getName(), b.getTypeName().getName()) && Objects.equals(b.getAttributeName(), attribute.getName()))
                                .forEach(staticData -> {
                                    if (log.isTraceEnabled()) {
                                        log.trace("    - found static data: {}, role: {}", staticData.getExpression(), staticData.getRole());
                                    }

                                    if (staticData.getRole() == AttributeBindingRole.GETTER) {
                                        if (staticData.getExpression() instanceof DataExpression) {
                                            bindings.getDataExpressions().put(attribute, (DataExpression) staticData.getExpression());
                                        } else {
                                            throw new IllegalStateException("Getter binding is not a ReferenceExpression");
                                        }
                                    }
                                });
                    });

            if (log.isTraceEnabled()) {
                log.trace("Collecting all references of unmapped transfer object type: {}", AsmUtils.getClassifierFQName(unmappedTransferObjectType));
            }
            unmappedTransferObjectType.getEAllReferences().stream()
                    .filter(r -> asmUtils.isMappedTransferObjectType(r.getEReferenceType()) && !AsmUtils.isEntityType(r.getEContainingClass()) && !asmUtils.isMappedTransferObjectType(r.getEContainingClass()) && r.isDerived())
                    .forEach(navigation -> {
                        log.trace("  - navigation: {}", AsmUtils.getReferenceFQName(navigation));
                        getBindings(ReferenceBinding.class, AsmUtils.getClassifierFQName(unmappedTransferObjectType).replace(".", "::")).stream()
                                .filter(b -> Objects.equals(unmappedTransferObjectType.getName(), b.getTypeName().getName()) && Objects.equals(b.getReferenceName(), navigation.getName()))
                                .forEach(staticNavigation -> {
                                    if (log.isTraceEnabled()) {
                                        log.trace("    - found static navigation: {}, role: {}", staticNavigation.getExpression(), staticNavigation.getRole());
                                    }

                                    if (staticNavigation.getRole() == ReferenceBindingRole.GETTER) {
                                        if (staticNavigation.getExpression() instanceof ReferenceExpression) {
                                            bindings.getNavigationExpressions().put(navigation, (ReferenceExpression) staticNavigation.getExpression());
                                        } else {
                                            throw new IllegalStateException("Getter binding is not a ReferenceExpression");
                                        }
                                    }
                                });
                    });

            return Optional.of(bindings);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Get expression tree of a given transfer object graph.
     *
     * @param mappedTransferObjectType mapped transfer object type
     * @return root node of expression tree
     */
    public Optional<MappedTransferObjectTypeBindings> getTransferObjectGraph(final EClass mappedTransferObjectType) {
        return getTransferObjectGraph(mappedTransferObjectType, new ConcurrentHashMap<>());
    }

    public Optional<MappedTransferObjectTypeBindings> getTransferObjectGraph(final EClass mappedTransferObjectType, final Map<EClass, MappedTransferObjectTypeBindings> processedMappedTransferObjectTypeBindings) {
        final Optional<EClass> mappedEntityType = asmUtils.getMappedEntityType(mappedTransferObjectType);

        if (!mappedEntityType.isPresent()) {
            if (log.isDebugEnabled()) {
                log.debug("Invalid mapped transfer object type: {}", AsmUtils.getClassifierFQName(mappedTransferObjectType));
            }

            return Optional.empty();
        } else {
            if (processedMappedTransferObjectTypeBindings.containsKey(mappedEntityType.get())) {
                return Optional.of(processedMappedTransferObjectTypeBindings.get(mappedEntityType.get()));
            }

            final MappedTransferObjectTypeBindings mappedTransferObjectTypeBindings = MappedTransferObjectTypeBindings.builder()
                    .entityType(mappedEntityType.get())
                    .transferObjectType(mappedTransferObjectType)
                    .build();
            final EntityTypeExpressions entityTypeExpressions = entityTypeExpressionsMap.get(mappedEntityType.get());

            final Optional<TypeName> typeName = getBindings(TypeName.class, AsmUtils.getClassifierFQName(mappedEntityType.get()).replace(".", "::")).stream()
                    .findFirst();

            if (!typeName.isPresent()) {
                log.warn("No type name for entity type: {}", AsmUtils.getClassifierFQName(mappedEntityType.get()));
            }

            if (log.isTraceEnabled()) {
                log.trace("Collecting all attributes of mapped transfer object type: {}, mapped entity type: {}", AsmUtils.getClassifierFQName(mappedTransferObjectType), AsmUtils.getClassifierFQName(mappedEntityType.get()));
            }
            mappedTransferObjectType.getEAllAttributes().stream()
                    .forEach(transferAttribute -> {
                        final Optional<String> featureName = AsmUtils.getExtensionAnnotationValue(transferAttribute, "binding", false);
                        if (featureName.isPresent() && typeName.isPresent()) {
                            if (log.isTraceEnabled()) {
                                log.trace("  - transfer attribute: {}, bound to {}", AsmUtils.getAttributeFQName(transferAttribute), featureName.get());
                            }

                            final Optional<EAttribute> attribute = mappedEntityType.get().getEAllAttributes().stream().filter(a -> Objects.equals(a.getName(), featureName.get())).findAny();
                            if (attribute.isPresent()) {
                                if (log.isTraceEnabled()) {
                                    log.trace("      - found binding {}", AsmUtils.getAttributeFQName(attribute.get()));
                                }
                                final EClass attributeClass = attribute.get().getEContainingClass();

                                getBindings(AttributeBinding.class, AsmUtils.getClassifierFQName(attributeClass).replace(".", "::")).stream()
                                        .filter(b -> Objects.equals(attributeClass.getName(), b.getTypeName().getName()) && Objects.equals(b.getAttributeName(), featureName.get()))
                                        .forEach(attributeBinding -> {
                                            if (log.isTraceEnabled()) {
                                                log.trace("        - found expression binding: {}, role: {}", attributeBinding.getExpression(), attributeBinding.getRole());
                                            }

                                            switch (attributeBinding.getRole()) {
                                                case GETTER:
                                                    if (attributeBinding.getExpression() instanceof DataExpression) {
                                                        if (!staticFlagOfAttributes.containsKey(attribute.get())) {
                                                            final List<Variable> variables = new ArrayList<>(expressionEvaluator.getVariablesOfScope(attributeBinding.getExpression()));
                                                            staticFlagOfAttributes.put(attribute.get(), variables);
                                                        }

                                                        mappedTransferObjectTypeBindings.getGetterAttributeExpressions().put(transferAttribute, (DataExpression) attributeBinding.getExpression());
                                                        asmUtils.getMappedAttribute(transferAttribute)
                                                                .ifPresent(entityAttribute -> entityTypeExpressions.getGetterAttributeExpressions().put(entityAttribute, (DataExpression) attributeBinding.getExpression()));
                                                    } else {
                                                        throw new IllegalStateException("Getter binding is not a DataExpression: " + AsmUtils.getAttributeFQName(attribute.get()));
                                                    }
                                                    break;
                                                case SETTER:
                                                    if (attributeBinding.getExpression() instanceof DataExpression) {
                                                        mappedTransferObjectTypeBindings.getSetterAttributeExpressions().put(transferAttribute, (DataExpression) attributeBinding.getExpression());
                                                    } else {
                                                        throw new IllegalStateException("Setter binding is not a DataExpression: " + AsmUtils.getAttributeFQName(attribute.get()));
                                                    }
                                                    break;
                                            }
                                        });
                            } else {
                                throw new IllegalStateException("Attribute not found");
                            }
                        }
                    });

            if (log.isTraceEnabled()) {
                log.trace("Collecting all references of mapped transfer object type: {}, mapped entity type: {}", AsmUtils.getClassifierFQName(mappedTransferObjectType), AsmUtils.getClassifierFQName(mappedEntityType.get()));
            }
            mappedTransferObjectType.getEAllReferences().forEach(transferRelation -> {
                final Optional<String> featureName = AsmUtils.getExtensionAnnotationValue(transferRelation, "binding", false);
                if (featureName.isPresent() && typeName.isPresent()) {
                    if (log.isTraceEnabled()) {
                        log.trace("  - transfer relation: {}, bound to {}", AsmUtils.getReferenceFQName(transferRelation), featureName.get());
                    }

                    final Optional<EReference> reference = mappedEntityType.get().getEAllReferences().stream().filter(r -> Objects.equals(r.getName(), featureName.get())).findAny();
                    if (reference.isPresent()) {
                        if (log.isTraceEnabled()) {
                            log.trace("      - found binding {}", AsmUtils.getReferenceFQName(reference.get()));
                        }
                        final EClass referenceClass = reference.get().getEContainingClass();

                        getBindings(ReferenceBinding.class, AsmUtils.getClassifierFQName(referenceClass).replace(".", "::")).stream()
                                .filter(b -> Objects.equals(referenceClass.getName(), b.getTypeName().getName()) && Objects.equals(b.getReferenceName(), featureName.get()))
                                .forEach(referenceBinding -> {
                                    if (log.isTraceEnabled()) {
                                        log.trace("        - found expression binding: {}, role: {}", referenceBinding.getExpression(), referenceBinding.getRole());
                                    }

                                    switch (referenceBinding.getRole()) {
                                        case GETTER:
                                            if (referenceBinding.getExpression() instanceof ReferenceExpression) {
                                                if (!staticFlagOfReferences.containsKey(reference.get())) {
                                                    final List<Variable> variables = new ArrayList<>(expressionEvaluator.getVariablesOfScope(referenceBinding.getExpression()));
                                                    staticFlagOfReferences.put(reference.get(), variables);
                                                }

                                                mappedTransferObjectTypeBindings.getGetterReferenceExpressions().put(transferRelation, (ReferenceExpression) referenceBinding.getExpression());
                                                asmUtils.getMappedReference(transferRelation)
                                                        .ifPresent(entityRelation -> entityTypeExpressions.getGetterReferenceExpressions().put(entityRelation, (ReferenceExpression) referenceBinding.getExpression()));
                                            } else {
                                                throw new IllegalStateException("Getter binding is not a ReferenceExpression");
                                            }
                                            break;
                                        case SETTER:
                                            if (referenceBinding.getExpression() instanceof ReferenceExpression) {
                                                mappedTransferObjectTypeBindings.getSetterReferenceExpressions().put(transferRelation, (ReferenceExpression) referenceBinding.getExpression());
                                            } else {
                                                throw new IllegalStateException("Setter binding is not a ReferenceExpression");
                                            }
                                            break;
                                    }
                                });
                    } else {
                        throw new IllegalStateException("Reference not found");
                    }
                }
            });


            getBindings(FilterBinding.class, AsmUtils.getClassifierFQName(mappedTransferObjectType).replace(".", "::")).stream()
                    .forEach(filterBinding -> {
                        if (log.isTraceEnabled()) {
                            log.trace("        - found filter binding: {}", filterBinding.getExpression());
                        }

                        if (filterBinding.getExpression() instanceof LogicalExpression) {
                            mappedTransferObjectTypeBindings.setFilter((LogicalExpression) filterBinding.getExpression());
                        } else {
                            throw new IllegalStateException("Filter  binding is not a LogicalExpression");
                        }
                    });

            processedMappedTransferObjectTypeBindings.put(mappedTransferObjectType, mappedTransferObjectTypeBindings);

            mappedTransferObjectType.getEAllReferences().forEach(reference -> {
                if (processedMappedTransferObjectTypeBindings.containsKey(reference.getEReferenceType())) {
                    mappedTransferObjectTypeBindings.getReferences().put(reference, processedMappedTransferObjectTypeBindings.get(reference.getEReferenceType()));
                } else {
                    final Optional<MappedTransferObjectTypeBindings> referenceNode = getTransferObjectGraph(reference.getEReferenceType(), processedMappedTransferObjectTypeBindings);
                    if (referenceNode.isPresent()) {
                        mappedTransferObjectTypeBindings.getReferences().put(reference, referenceNode.get());
                    }
                }
            });

            return Optional.of(mappedTransferObjectTypeBindings);
        }
    }

    public Boolean isStaticReference(EReference reference) {
        if (!staticFlagOfReferences.containsKey(reference)) {
            return null;
        } else {
            return staticFlagOfReferences.get(reference).isEmpty();
        }
    }

    public Boolean isStaticAttribute(EAttribute attribute) {
        if (!staticFlagOfAttributes.containsKey(attribute)) {
            return null;
        } else {
            return staticFlagOfAttributes.get(attribute).isEmpty();
        }
    }

    /**
     * Get the entity attributes whose GETTER binding was evaluated with an empty variable list (static attributes).
     * Never evaluated and non-static attributes are not included.
     *
     * @return unmodifiable set of static entity attributes
     */
    public Set<EAttribute> getStaticEntityAttributes() {
        return staticFlagOfAttributes.entrySet().stream()
                .filter(e -> e.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .collect(Collectors.collectingAndThen(Collectors.toSet(), Collections::unmodifiableSet));
    }

    /**
     * Get the entity references whose GETTER binding was evaluated with an empty variable list (static references).
     * Never evaluated and non-static references are not included.
     *
     * @return unmodifiable set of static entity references
     */
    public Set<EReference> getStaticEntityReferences() {
        return staticFlagOfReferences.entrySet().stream()
                .filter(e -> e.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .collect(Collectors.collectingAndThen(Collectors.toSet(), Collections::unmodifiableSet));
    }

    /**
     * Binding classes that are answered by the binding index.
     */
    static final List<Class<? extends EObject>> INDEXED_BINDING_CLASSES = Collections.unmodifiableList(Arrays.asList(
            TypeName.class, AttributeBinding.class, ReferenceBinding.class, FilterBinding.class));

    /**
     * Get the expression model elements of a given binding class that belong to a type (FQN as {@code namespace::name}),
     * in the encounter order of {@code ResourceSet.getAllContents()}. Answered from the binding index that is built once
     * over the whole expression resource set.
     *
     * @param bindingClass one of {@link TypeName}, {@link AttributeBinding}, {@link ReferenceBinding}, {@link FilterBinding}
     * @param typeFqn      type FQN with {@code ::} separator
     * @param <T>          binding type
     * @return unmodifiable list of elements, empty when there is none
     */
    @SuppressWarnings("unchecked")
    <T> List<T> getBindings(final Class<T> bindingClass, final String typeFqn) {
        final Map<String, List<EObject>> byType = bindingIndex.get(bindingClass);
        if (byType == null) {
            throw new IllegalArgumentException("Binding class is not indexed: " + bindingClass.getName());
        }
        return (List<T>) byType.getOrDefault(typeFqn, Collections.emptyList());
    }

    /**
     * Get the type FQNs (as {@code namespace::name}) known by the binding index for a binding class.
     *
     * @param bindingClass indexed binding class
     * @return type FQNs
     */
    Set<String> getIndexedTypeNames(final Class<?> bindingClass) {
        final Map<String, List<EObject>> byType = bindingIndex.get(bindingClass);
        if (byType == null) {
            throw new IllegalArgumentException("Binding class is not indexed: " + bindingClass.getName());
        }
        return Collections.unmodifiableSet(byType.keySet());
    }

    /**
     * Build the binding index with one pass over the whole expression resource set.
     */
    private Map<Class<?>, Map<String, List<EObject>>> buildBindingIndex() {
        final Map<Class<?>, Map<String, List<EObject>>> index = new HashMap<>();
        INDEXED_BINDING_CLASSES.forEach(c -> index.put(c, new HashMap<>()));

        final Iterator<Notifier> contents = expressionResourceSet.getAllContents();
        while (contents.hasNext()) {
            final Notifier notifier = contents.next();
            if (notifier instanceof EObject) {
                final EObject element = (EObject) notifier;
                for (Class<? extends EObject> bindingClass : INDEXED_BINDING_CLASSES) {
                    if (bindingClass.isInstance(element)) {
                        final TypeName typeName = element instanceof TypeName ? (TypeName) element : bindingTypeName(element);
                        if (typeName != null) {
                            index.get(bindingClass)
                                    .computeIfAbsent(typeName.getNamespace() + "::" + typeName.getName(), k -> new ArrayList<>())
                                    .add(element);
                        }
                    }
                }
            }
        }

        // freeze
        index.replaceAll((c, byType) -> {
            byType.replaceAll((k, v) -> Collections.unmodifiableList(v));
            return Collections.unmodifiableMap(byType);
        });
        return Collections.unmodifiableMap(index);
    }

    private static TypeName bindingTypeName(final EObject element) {
        if (element instanceof AttributeBinding) {
            return ((AttributeBinding) element).getTypeName();
        } else if (element instanceof ReferenceBinding) {
            return ((ReferenceBinding) element).getTypeName();
        } else if (element instanceof FilterBinding) {
            return ((FilterBinding) element).getTypeName();
        }
        return null;
    }
}
