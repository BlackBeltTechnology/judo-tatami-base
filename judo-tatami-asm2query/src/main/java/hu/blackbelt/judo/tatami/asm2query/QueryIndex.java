package hu.blackbelt.judo.tatami.asm2query;

/*-
 * #%L
 * JUDO Tatami parent
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
import hu.blackbelt.judo.meta.expression.DataExpression;
import hu.blackbelt.judo.meta.expression.ReferenceExpression;
import hu.blackbelt.judo.meta.query.DataQueryEntry;
import hu.blackbelt.judo.meta.query.EntityTypeExpressionsEntry;
import hu.blackbelt.judo.meta.query.GetterAttributeEntry;
import hu.blackbelt.judo.meta.query.GetterReferenceEntry;
import hu.blackbelt.judo.meta.query.NavigationEntry;
import hu.blackbelt.judo.meta.query.QueryEntry;
import hu.blackbelt.judo.meta.query.QueryModelInfo;
import hu.blackbelt.judo.meta.query.Select;
import hu.blackbelt.judo.meta.query.SubSelect;
import hu.blackbelt.judo.meta.query.runtime.QueryModel;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Index over the logical query model: queries of mapped transfer object types, static navigations and static data
 * queries, ordered transfer relations, static entity features, the entity type expressions and the source / target
 * counters.
 *
 * <p>The same type answers for a freshly built query model ({@link Asm2Query}) and for a persisted one
 * ({@link #restore(ResourceSet, QueryModel)}), with the same concurrent collection types. The static answers follow one
 * rule in both cases: {@code getMappedX(feature).map(staticSet::contains).orElse(true)}.</p>
 */
@Slf4j
public class QueryIndex {

    private final AsmUtils asmUtils;

    private final Map<EClass, Select> transferObjectQueries = new ConcurrentHashMap<>();
    private final Map<EReference, SubSelect> navigationQueries = new ConcurrentHashMap<>();
    private final Map<EAttribute, SubSelect> dataQueries = new ConcurrentHashMap<>();
    private final Set<EReference> orderedTransferRelations = ConcurrentHashMap.newKeySet();
    private final Set<EAttribute> staticEntityAttributes = ConcurrentHashMap.newKeySet();
    private final Set<EReference> staticEntityReferences = ConcurrentHashMap.newKeySet();
    private final Map<EClass, EntityTypeExpressions> entityTypeExpressionsMap = new ConcurrentHashMap<>();

    private final AtomicInteger nextSourceIndex;
    private final AtomicInteger nextTargetIndex;

    QueryIndex(@NonNull final AsmUtils asmUtils,
               final Map<EClass, Select> transferObjectQueries,
               final Map<EReference, SubSelect> navigationQueries,
               final Map<EAttribute, SubSelect> dataQueries,
               final Set<EReference> orderedTransferRelations,
               final Set<EAttribute> staticEntityAttributes,
               final Set<EReference> staticEntityReferences,
               final Map<EClass, EntityTypeExpressions> entityTypeExpressionsMap,
               @NonNull final AtomicInteger nextSourceIndex,
               @NonNull final AtomicInteger nextTargetIndex) {
        this.asmUtils = asmUtils;
        this.transferObjectQueries.putAll(transferObjectQueries);
        this.navigationQueries.putAll(navigationQueries);
        this.dataQueries.putAll(dataQueries);
        this.orderedTransferRelations.addAll(orderedTransferRelations);
        this.staticEntityAttributes.addAll(staticEntityAttributes);
        this.staticEntityReferences.addAll(staticEntityReferences);
        this.entityTypeExpressionsMap.putAll(entityTypeExpressionsMap);
        this.nextSourceIndex = nextSourceIndex;
        this.nextTargetIndex = nextTargetIndex;
    }

    /**
     * Get logical query of a given transfer object type.
     * Logical query contains all information required to create SQL SELECT statement on ASM and no SQL table and
     * column names and navigation (ID, foreign key name) is resolved.
     *
     * @param transferObjectType mapped transfer object type
     * @return logical query
     */
    public Optional<Select> getQuery(final EClass transferObjectType) {
        if (!asmUtils.isMappedTransferObjectType(transferObjectType)) {
            log.error("Argument is not mapped transfer object type: {}", AsmUtils.getClassifierFQName(transferObjectType));
        }

        return Optional.ofNullable(transferObjectQueries.get(transferObjectType));
    }

    public Optional<SubSelect> getNavigation(final EReference navigation) {
        return Optional.ofNullable(navigationQueries.get(navigation));
    }

    public Optional<SubSelect> getDataQuery(final EAttribute attribute) {
        return Optional.ofNullable(dataQueries.get(attribute));
    }

    public boolean isOrdered(final EReference transferObjectRelation) {
        return orderedTransferRelations.contains(transferObjectRelation);
    }

    /**
     * Static answer for a transfer object relation: {@code true} for relations without a mapped entity relation, and
     * for those whose mapped entity relation is in the static set.
     */
    public boolean isStaticReference(final EReference reference) {
        return asmUtils.getMappedReference(reference)
                .map(staticEntityReferences::contains)
                .orElse(true); // static=true returned for transfer object relations without binding
    }

    /**
     * Static answer for a transfer attribute: {@code true} for attributes without a mapped entity attribute, and
     * for those whose mapped entity attribute is in the static set.
     */
    public boolean isStaticAttribute(final EAttribute attribute) {
        return asmUtils.getMappedAttribute(attribute)
                .map(staticEntityAttributes::contains)
                .orElse(true); // static=true returned for transfer attributes without binding
    }

    /**
     * @return map of every entity type to its getter expressions (entries of entity types without getters included)
     */
    public Map<EClass, EntityTypeExpressions> getEntityTypeExpressionsMap() {
        return Collections.unmodifiableMap(entityTypeExpressionsMap);
    }

    public AtomicInteger getNextSourceIndex() {
        return nextSourceIndex;
    }

    public AtomicInteger getNextTargetIndex() {
        return nextTargetIndex;
    }

    /**
     * @return keys of the transfer object queries (mapped transfer object types)
     */
    public Set<EClass> getQueryTypes() {
        return Collections.unmodifiableSet(transferObjectQueries.keySet());
    }

    public Set<EReference> getNavigationReferences() {
        return Collections.unmodifiableSet(navigationQueries.keySet());
    }

    public Set<EAttribute> getDataQueryAttributes() {
        return Collections.unmodifiableSet(dataQueries.keySet());
    }

    public Set<EReference> getOrderedTransferRelations() {
        return Collections.unmodifiableSet(orderedTransferRelations);
    }

    public Set<EAttribute> getStaticEntityAttributes() {
        return Collections.unmodifiableSet(staticEntityAttributes);
    }

    public Set<EReference> getStaticEntityReferences() {
        return Collections.unmodifiableSet(staticEntityReferences);
    }

    /**
     * Restore the index from the single {@link QueryModelInfo} root of a query model resource.
     *
     * @param asmResourceSet live ASM resource set the model references resolve into
     * @param queryModel     query model (built or loaded)
     * @return restored index
     * @throws IllegalStateException if the resource has no, or more than one, {@code QueryModelInfo} root, or the info
     *                               is not restorable
     */
    public static QueryIndex restore(@NonNull final ResourceSet asmResourceSet, @NonNull final QueryModel queryModel) {
        return restore(new AsmUtils(asmResourceSet), getQueryModelInfo(queryModel.getResource()));
    }

    /**
     * Find the single {@link QueryModelInfo} root of a resource. Fails on none or several; never picks the first.
     */
    public static QueryModelInfo getQueryModelInfo(@NonNull final Resource resource) {
        final List<QueryModelInfo> infos = resource.getContents().stream()
                .filter(QueryModelInfo.class::isInstance)
                .map(QueryModelInfo.class::cast)
                .collect(Collectors.toList());
        if (infos.size() != 1) {
            throw new IllegalStateException("Query model must have exactly one QueryModelInfo root, found: " + infos.size()
                    + " (" + resource.getURI() + ")");
        }
        return infos.get(0);
    }

    /**
     * Restore the index from a {@link QueryModelInfo}.
     *
     * <p>The format version is a stamp: an unset (absent) stamp reads as {@code 0} and is a mismatch, never a match.</p>
     *
     * @param asmUtils ASM utils over the live ASM resource set
     * @param info     query model info
     * @return restored index
     * @throws IllegalStateException if the format version is not {@link Asm2Query#FORMAT_VERSION} or an entry is incomplete
     */
    public static QueryIndex restore(@NonNull final AsmUtils asmUtils, @NonNull final QueryModelInfo info) {
        if (info.getFormatVersion() != Asm2Query.FORMAT_VERSION) {
            throw new IllegalStateException("Unsupported query model format version: " + info.getFormatVersion()
                    + ", expected: " + Asm2Query.FORMAT_VERSION);
        }

        final Map<EClass, Select> queries = new ConcurrentHashMap<>();
        for (QueryEntry entry : info.getQueries()) {
            queries.put(required(entry.getType(), "query type"), required(entry.getSelect(), "query select"));
        }

        final Map<EReference, SubSelect> navigations = new ConcurrentHashMap<>();
        for (NavigationEntry entry : info.getNavigations()) {
            navigations.put(required(entry.getReference(), "navigation reference"), required(entry.getSubSelect(), "navigation sub-select"));
        }

        final Map<EAttribute, SubSelect> dataQueries = new ConcurrentHashMap<>();
        for (DataQueryEntry entry : info.getDataQueries()) {
            dataQueries.put(required(entry.getAttribute(), "data query attribute"), required(entry.getSubSelect(), "data query sub-select"));
        }

        final Map<EClass, EntityTypeExpressions> entityTypeExpressions = new ConcurrentHashMap<>();
        for (EntityTypeExpressionsEntry entry : info.getEntityTypeExpressions()) {
            final EntityTypeExpressions expressions = EntityTypeExpressions.builder()
                    .entityType(required(entry.getEntityType(), "entity type"))
                    .build();
            for (GetterAttributeEntry getter : entry.getGetterAttributes()) {
                expressions.getGetterAttributeExpressions().put(
                        required(getter.getAttribute(), "getter attribute"),
                        expression(getter.getExpression(), DataExpression.class));
            }
            for (GetterReferenceEntry getter : entry.getGetterReferences()) {
                expressions.getGetterReferenceExpressions().put(
                        required(getter.getReference(), "getter reference"),
                        expression(getter.getExpression(), ReferenceExpression.class));
            }
            entityTypeExpressions.put(entry.getEntityType(), expressions);
        }

        return new QueryIndex(asmUtils,
                queries,
                navigations,
                dataQueries,
                resolved(info.getOrderedTransferRelations(), "ordered transfer relation"),
                resolved(info.getStaticEntityAttributes(), "static entity attribute"),
                resolved(info.getStaticEntityReferences(), "static entity reference"),
                entityTypeExpressions,
                new AtomicInteger(info.getNextSourceIndex()),
                new AtomicInteger(info.getNextTargetIndex()));
    }

    private static <T> T required(final T value, final String what) {
        if (value == null || (value instanceof EObject && ((EObject) value).eIsProxy())) {
            throw new IllegalStateException("Query model info entry has no resolved " + what + ": " + value);
        }
        return value;
    }

    private static <T extends EObject> Set<T> resolved(final List<T> values, final String what) {
        final Set<T> result = ConcurrentHashMap.newKeySet();
        values.forEach(v -> result.add(required(v, what)));
        return result;
    }

    private static <T extends EObject> T expression(final EObject expression, final Class<T> expectedType) {
        if (expression == null || expression.eIsProxy() || !expectedType.isInstance(expression)) {
            throw new IllegalStateException("Query model info getter expression is not a resolved "
                    + expectedType.getSimpleName() + ": " + expression);
        }
        return expectedType.cast(expression);
    }
}
